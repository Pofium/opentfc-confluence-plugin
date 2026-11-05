---
kind: logging_system
name: 'Dual Logging: SLF4J for Application Code + In-Memory Ring Buffer Diagnostics with Optional Log4j2 Host Capture'
category: logging_system
scope:
    - '**'
source_files:
    - src/main/java/org/opentfc/diag/OpenTfcLogStore.java
    - src/main/java/org/opentfc/diag/HostCapture.java
    - src/main/java/org/opentfc/diag/HostLogCaptureInstaller.java
    - src/main/java/org/opentfc/diag/Log4jRingAppender.java
    - src/main/java/org/opentfc/diag/PluginLifecycleLogger.java
    - src/main/java/org/opentfc/diag/OpenTfcDiagnosticsServlet.java
    - diag-lite/src/main/java/org/opentfc/diag/lite/LiteLogStore.java
    - diag-lite/src/main/java/org/opentfc/diag/lite/LiteRingAppender.java
    - diag-lite/src/main/java/org/opentfc/diag/lite/LiteCaptureInstaller.java
    - pom.xml
---

## Overview

The OpenTFC Confluence plugin uses two complementary logging subsystems:

1. **Application logging via SLF4J** — the standard `org.slf4j.Logger` API, bound at runtime by Confluence's own SLF4J implementation.
2. **Diagnostic in-memory log store** — a dependency-free ring buffer (`OpenTfcLogStore`, capacity 2000) that is rendered on the diagnostics servlet page and also receives host WARN/ERROR entries captured from the Confluence process via an optional Log4j2 appender.

A companion standalone plugin `diag-lite` ships an identical but independent ring buffer (`LiteLogStore`) so diagnostics remain available even when the main plugin cannot activate.

## Frameworks and Dependencies

- **SLF4J API** (`org.slf4j:slf4j-api`, scope `provided`) is used by all application code (macros, REST resources, lifecycle logger).
- **SLF4J Simple** (`org.slf4j:slf4j-simple:1.7.36`, scope `test`) is the test-time binding.
- **Log4j2 API + Core** (`org.apache.logging.log4j:log4j-api:2.21.1`, `log4j-core:2.21.1`, both scope `provided`) are imported only for the host-log-capture feature; they are declared `resolution:="optional"` in the OSGi `Import-Package` so the plugin still loads without them.
- The POM pins these versions explicitly under `<dependencyManagement>`.

## Key Files

| File | Role |
|---|---|
| `src/main/java/org/opentfc/diag/OpenTfcLogStore.java` | Thread-safe in-memory ring buffer of diagnostic entries (level, source, message, truncated stack trace). Exposed to the diagnostics servlet. |
| `src/main/java/org/opentfc/diag/HostCapture.java` | Guarded entry point that attempts to install the Log4j2 host capture; catches `NoClassDefFoundError` / `ClassNotFoundException` and logs a warning into `OpenTfcLogStore`. |
| `src/main/java/org/opentfc/diag/HostLogCaptureInstaller.java` | Performs the actual Log4j2 wiring: creates a `LoggerContext`, adds `Log4jRingAppender` as a root appender at `Level.WARN`, calls `ctx.updateLoggers()`. |
| `src/main/java/org/opentfc/diag/Log4jRingAppender.java` | Custom `AbstractAppender` that forwards every host `WARN`/`ERROR` event into `OpenTfcLogStore.record(...)`, truncating logger names > 60 chars. |
| `src/main/java/org/opentfc/diag/PluginLifecycleLogger.java` | Spring `@Named` component implementing `InitializingBean`/`DisposableBean`; registers Atlassian `EventListener`s for `PluginEnabledEvent`/`PluginDisabledEvent`/`PluginFrameworkStartedEvent` and writes them into `OpenTfcLogStore` under the `lifecycle` / `plugin-framework` sources. |
| `src/main/java/org/opentfc/diag/OpenTfcDiagnosticsServlet.java` | Serves `/plugins/servlet/opentfc/diagnostics`, rendering `OpenTfcLogStore.asText()` plus status flags. |
| `diag-lite/src/main/java/org/opentfc/diag/lite/LiteLogStore.java` | Standalone copy of the ring buffer for the diag-lite plugin. |
| `diag-lite/src/main/java/org/opentfc/diag/lite/LiteRingAppender.java` | Diag-lite counterpart of `Log4jRingAppender`. |
| `diag-lite/src/main/java/org/opentfc/diag/lite/LiteCaptureInstaller.java` | Diag-lite counterpart of `HostLogCaptureInstaller`. |

## Architecture and Conventions

### Two-tier logging model

- **Business code** (macros, REST resources, services) logs through SLF4J (`LoggerFactory.getLogger(...)`), e.g. `BaseOpenTfcMacro.warn(...)`, `ExportRestResource.error(...)`, `SourceRestResource.error(...)`.
- **Diagnostic code** writes structured entries into `OpenTfcLogStore.info(source, message)` / `.warn(...)` / `.error(source, message, throwable)`. Each entry carries four fields: `timestamp` (epoch millis), `level` (string `INFO`/`WARN`/`ERROR`), `source` (a short category tag such as `macro`, `rest-source`, `lifecycle`, `plugin-framework`, `diag`), and `message` (with up to 15 lines of stack trace appended when a `Throwable` is supplied).

### Ring buffer semantics

- Capacity is fixed at `MAX_ENTRIES = 2000` (documented comment: "must survive a plugin enable/disable storm").
- When full, the oldest entry is evicted and `DROPPED` counter is incremented.
- All mutations are synchronized on the `ENTRIES` deque; `snapshot()` returns an unmodifiable copy.
- `asText()` produces a human-readable line format: `<ISO timestamp> <LEVEL>[source] message`.
- `clear()` resets the buffer and records an `INFO` entry noting the clear action.

### Host log capture (optional)

- `HostCapture.tryInstall()` wraps the entire Log4j2 wiring in a try/catch around `NoClassDefFoundError` / `ClassNotFoundException` so missing log4j-core does not prevent the plugin from loading.
- `HostLogCaptureInstaller.install()` checks whether an appender named `OpenTfcHostCapture` already exists (idempotent re-install); if not, it creates `Log4jRingAppender`, starts it, adds it to the root logger at `Level.WARN`, and calls `ctx.updateLoggers()`.
- Only `WARN` and above from the host process are captured — this is the minimum needed to detect cascading plugin failures.

### Lifecycle instrumentation

`PluginLifecycleLogger` is the central place where plugin framework events become visible on the diagnostics page. It:
- Sets `OpenTfcLogStore.lifecycleUp = true` during `afterPropertiesSet()`.
- Calls `HostCapture.tryInstall()` eagerly.
- Logs `ENABLED` / `DISABLED` / `Plugin framework started` messages under the `plugin-framework` source.
- Uses SLF4J `log.debug("OpenTFC: cannot read plugin key from event", t)` only for non-fatal introspection failures inside `pluginKey()`.

### Source tagging convention

Callers choose a short `source` string that groups related entries. Observed values include:
- `diag` — internal diagnostics plumbing
- `lifecycle` — Spring context startup / shutdown
- `plugin-framework` — Atlassian plugin enabled/disabled events
- `macro` — macro rendering
- `rest-source` / `rest-*` — REST resource operations
- `diag-lite` — the companion standalone plugin

These strings appear verbatim in the `[source]` column of the diagnostics page.

### Level strategy

- `INFO` — normal operational milestones (store init, macro render, framework started, capture installed).
- `WARN` — recoverable problems or degraded state (event registration failure, raw-body fallback, host capture unavailable, plugin disabled).
- `ERROR` — unrecoverable failures (CSV/XLSX export failure, source data retrieval failure, listener registration exception).
- No `DEBUG` usage against `OpenTfcLogStore`; debug-level output goes only through SLF4J in the business layer.

## Rules Enforced by the Code

1. **Host log capture is optional and guarded.** `HostLogCaptureInstaller` and `HostCapture` catch class-loading errors so the rest of the plugin remains functional when `log4j-core` is absent. This is enforced by the explicit try/catch blocks and the `resolution:="optional"` OSGi import.
2. **The ring buffer never grows beyond 2000 entries.** `record()` polls the head before adding and increments `DROPPED`; tests can assert `droppedCount()`.
3. **Stack traces are truncated to 15 lines.** Both `OpenTfcLogStore.firstStackLines` and `LiteLogStore.firstStackLines` split on `\r?\n` and cap at `MAX_STACK_LINES = 15`, appending a `... (N more lines)` footer when exceeded.
4. **Host logger names are capped at 60 characters.** `Log4jRingAppender.append()` truncates any logger name longer than 60 chars to its tail, preventing oversized columns in the diagnostics UI.
5. **Reinstalling host capture is idempotent.** `HostLogCaptureInstaller.install()` checks `config.getAppender("OpenTfcHostCapture") != null` before doing anything.
6. **All public accessors on `OpenTfcLogStore` are thread-safe.** Mutations and snapshots synchronize on the shared `ENTRIES` deque; `droppedCount()` uses `AtomicLong.get()`.
7. **Diagnostic entries carry a `source` category.** Every call to `OpenTfcLogStore.info/warn/error` requires a first argument identifying the subsystem; there is no parameterless variant.
8. **Test bindings use SLF4J Simple.** The POM declares `slf4j-simple` as a test-scoped dependency, so unit tests print to stdout rather than requiring a real SLF4J backend.