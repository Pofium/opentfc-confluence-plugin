---
kind: error_handling
name: Error Handling in OpenTFC Confluence Plugin
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/org/opentfc/macro/BaseOpenTfcMacro.java
    - src/main/java/org/opentfc/service/SqlTransformerEngine.java
    - src/main/java/org/opentfc/model/SqlExecutionResult.java
    - src/main/java/org/opentfc/rest/ExportRestResource.java
    - src/main/java/org/opentfc/rest/SourceRestResource.java
    - src/main/java/org/opentfc/service/DataSourceResolver.java
    - src/main/java/org/opentfc/diag/HostCapture.java
    - src/main/java/org/opentfc/diag/PluginLifecycleLogger.java
    - src/main/java/org/opentfc/admin/OpenTfcAdminAction.java
---

## Overview

The OpenTFC Confluence plugin does not define a custom exception hierarchy or sentinel-error types. Instead, it uses a pragmatic mix of:

- **Checked exceptions** propagated through JAX-RS REST endpoints (`IOException`, `SQLException`)
- **Broad `catch (Exception e)` / `catch (Throwable t)` blocks** that log and degrade gracefully
- **Result objects** (`SqlExecutionResult`) for non-fatal engine errors
- **SLF4J logging** via `org.slf4j.Logger` plus the plugin's own `OpenTfcLogStore` ring buffer for diagnostics
- **No `try/finally` resource leaks**: most I/O is handled with try-with-resources where applicable

There is no global error middleware, no centralized error mapper, and no `@ExceptionHandler` — each layer handles its own failures.

## Key Files and Patterns

### Macro layer: graceful degradation
`BaseOpenTfcMacro.java` is the central point for macro rendering. It catches broad exceptions and falls back to raw content rather than failing the page render:

- `renderBody()` catches `Exception` from `xhtmlContent.convertStorageToView`, logs a warning, writes to `OpenTfcLogStore.warn`, and returns the original body.
- `toJson()` catches `Exception` from Jackson serialization and returns an empty `{}`.
- This ensures a single broken nested macro cannot break the whole page.

### SQL engine: structured result objects
`SqlTransformerEngine.executeQuery()` never throws to callers. All failure paths return `SqlExecutionResult.error(...)`:

- Empty input → `error("SQL query is empty", ...)`
- Disallowed SQL start keyword or multi-statement → `error("Security violation: only a single SELECT query is permitted", ...)`
- `TimeoutException` → cancels the statement/future and returns a timeout error message
- `ExecutionException` → unwraps the cause and rethrows as `SQLException`
- `SQLException` → generates a stable hex code `OTFC-SQL-<hash>` and returns a user-facing message
- Any other `Exception` → logs at `ERROR` level via both SLF4J and `OpenTfcLogStore.error`, then returns a generic `"Execution failed: <class>"` message

`SqlExecutionResult.java` is the canonical result type, carrying `success`, `table`, `executionTimeMs`, and `errorMessage`. Callers inspect `isSuccess()` instead of catching exceptions.

### REST layer: HTTP status mapping
REST resources convert exceptions into JAX-RS `Response` objects:

- `ExportRestResource`: `IOException` → `Response.serverError().entity("Export failed: " + msg)` after `log.error(...)`
- `SourceRestResource`: broad `Exception` → `Response.serverError().entity("Error loading source: " + msg)`, also logged to `OpenTfcLogStore.error("rest-source", ...)`
- Missing data sources → `Response.status(NOT_FOUND).entity("Source data stream could not be opened")`
- `ConfigRestResource` has no error path — it always returns `Response.ok(config)`

### Security boundary: explicit security exceptions
`DataSourceResolver.fetchSafeUrl()` throws typed exceptions for policy violations:

- `URISyntaxException | IllegalArgumentException` → wrapped in `IOException("Invalid URL", e)`
- Non-HTTP(S) protocol → `SecurityException("Forbidden URL protocol: ...")`
- Host not in whitelist → `SecurityException("Host is not in the OpenTFC URL whitelist: ...")`
- Internal IP detected → `SecurityException("SSRF blocked: internal address ...")`
- HTTP status ≠ 200 → `IOException("Remote server returned HTTP " + status)`
- Response exceeds `MAX_FETCH_SIZE` → `IOException("Remote file exceeds ... bytes")`

These are caught by `SourceRestResource`'s catch-all and surfaced as 500 responses.

### Diagnostics module: defensive `Throwable` catches
The diag sub-module is designed to keep running even when optional dependencies (e.g., `log4j-core`) are missing:

- `HostCapture.tryInstall()`: wraps `Class.forName(...)` in `catch (Throwable e)`, logs via `OpenTfcLogStore.warn`, returns `"unavailable (<exception>)"`.
- `PluginLifecycleLogger`: multiple `catch (Throwable ignored)` / `catch (Throwable t)` around Confluence component lookups so lifecycle logging never breaks plugin activation.
- `AdminAction.execute()` / `save()`: `catch (Throwable ignored)` around Confluence admin action execution.

This pattern is documented in the class-level comment on `HostLogCaptureInstaller`: *"`NoClassDefFoundError` only affects this class, not the whole plugin."*

### Logging conventions
Two logging channels coexist:

1. **SLF4J** (`org.slf4j.Logger`): used for operational errors — `log.error(...)`, `log.warn(...)`.
2. **`OpenTfcLogStore`**: a ring-buffered diagnostic store written to `/plugins/servlet/opentfc/diagnostics`. Used for structured, machine-readable events tagged by category strings like `"macro"`, `"sql-engine"`, `"rest-source"`, `"diag"`.

Categories used across the codebase: `macro`, `sql-engine`, `rest-source`, `diag`, `host-log-capture`.

## Conventions and Constraints

- **Macros degrade, they do not fail**: `BaseOpenTfcMacro` swallows exceptions from rich-body conversion and JSON serialization, returning fallback values. A broken nested macro should not prevent the page from rendering.
- **SQL engine returns results, not exceptions**: `SqlTransformerEngine` converts all error conditions (validation, timeout, SQL, unexpected) into `SqlExecutionResult.error(...)`. Callers check `success`.
- **REST endpoints map exceptions to HTTP 500**: every catch block in `ExportRestResource` and `SourceRestResource` returns `Response.serverError()` with a short user-facing message; the full stack trace goes to SLF4J and `OpenTfcLogStore`.
- **Security checks throw typed exceptions**: `DataSourceResolver` throws `SecurityException` for SSRF/whitelist violations and `IOException` for transport/parsing failures, allowing callers to distinguish policy vs. I/O errors.
- **Optional dependencies are guarded with `catch (Throwable)`**: the diag module deliberately catches `Throwable` (not just `Exception`) to survive `NoClassDefFoundError` and similar linkage failures.
- **No custom exception classes exist**: there is no `OpenTfcException`, no error-code enum, and no global error handler. Errors are represented either by standard Java exceptions or by the `SqlExecutionResult.success/error` factory methods.
- **Structured logging uses string-tag categories**: `OpenTfcLogStore.info/warn/error` calls consistently use a first-string category (`"macro"`, `"sql-engine"`, `"rest-source"`, `"diag"`) for filtering on the diagnostics page.