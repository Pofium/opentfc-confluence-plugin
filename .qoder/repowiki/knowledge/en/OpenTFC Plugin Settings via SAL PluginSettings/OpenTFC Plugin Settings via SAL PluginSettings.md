---
kind: configuration_system
name: OpenTFC Plugin Settings via SAL PluginSettings
category: configuration_system
scope:
    - '**'
source_files:
    - src/main/java/org/opentfc/service/OpenTfcSettings.java
    - src/main/java/org/opentfc/admin/OpenTfcAdminAction.java
    - src/main/resources/templates/admin/opentfc-admin.vm
    - src/main/resources/atlassian-plugin.xml
    - src/main/java/org/opentfc/rest/SourceRestResource.java
    - src/main/java/org/opentfc/diag/HomeDirProbe.java
    - diag-lite/src/main/java/org/opentfc/diag/lite/HomeDirProbe.java
---

## Approach

The plugin has no file-based configuration (no `application.properties`, YAML, TOML, or `.env`). Runtime configuration is provided exclusively through Atlassian's **SAL `PluginSettingsFactory`** / `PluginSettings` API, which stores values in Confluence's shared database and is cluster-safe for Data Center deployments. The only external system property consulted is `confluence.home` (read via `System.getProperty("confluence.home")`) by the diagnostics probes to locate log files.

## Key Files

- `src/main/java/org/opentfc/service/OpenTfcSettings.java` — single settings abstraction over `PluginSettings`
- `src/main/java/org/opentfc/admin/OpenTfcAdminAction.java` — XWork admin action that reads/writes settings
- `src/main/resources/templates/admin/opentfc-admin.vm` — Velocity template rendering the admin form
- `src/main/resources/atlassian-plugin.xml` — registers the `/admin/opentfc/*` XWork actions
- `src/main/java/org/opentfc/rest/SourceRestResource.java` — consumes the URL whitelist at runtime
- `diag-lite/src/main/java/org/opentfc/diag/lite/HomeDirProbe.java` + `src/main/java/org/opentfc/diag/HomeDirProbe.java` — read `confluence.home` for diagnostics

## Architecture & Conventions

1. **Centralized settings class.** `OpenTfcSettings` holds a `PluginSettingsFactory` and exposes typed getters (`getSqlTimeoutSeconds()`, `getMaxResultRows()`, `getUrlWhitelist()`). All callers go through this class; there are no direct `PluginSettings.get(...)` calls outside it.

2. **Key prefixing.** Every persisted key is prefixed with `org.opentfc.settings.` (constant `PREFIX`). Keys used:
   - `sqlTimeoutSeconds` — DuckDB SQL timeout in seconds
   - `maxResultRows` — maximum result rows for transformer queries
   - `allowedUrlWhitelist` — newline/comma/semicolon/space-delimited list of allowed domains

3. **Validation on both read and write.**
   - Read path: `getInt(key, def, min, max)` parses the stored string, falls back to default on `NumberFormatException`, and clamps the value into `[min, max]`. Defaults are `DEFAULT_SQL_TIMEOUT = 10` and `DEFAULT_MAX_ROWS = 50000`.
   - Write path: `save(...)` re-clamps values before persisting (`Math.max(1, Math.min(120, ...))` for timeout, `Math.max(100, Math.min(500000, ...))` for rows).

4. **URL whitelist parsing.** `getUrlWhitelistRaw()` returns the raw string; `getUrlWhitelist()` splits on `[\r\n,;\s]+`, trims each token, and filters empties. Consumers receive an immutable `List<String>`.

5. **Admin UI layer.** `OpenTfcAdminAction` is an XWork action under `/admin/opentfc/view-opentfc-admin.action`. It obtains `PluginSettingsFactory` either via Spring injection (`@ComponentImport`) or via `ComponentLocator.getComponent(...)` as a fallback for unit tests. The `save()` method is annotated `@RequireSecurityToken(true)` so Confluence enforces CSRF protection. The Velocity template renders three fields with inline `min`/`max` hints matching the Java-side bounds.

6. **Consumers.** `SourceRestResource` injects `OpenTfcSettings` and uses `settings.getUrlWhitelist()` (falling back to `List.of("*")` when settings is unavailable) to gate CSV/JSON source URLs. Other modules reference defaults directly from `OpenTfcSettings.DEFAULT_*` constants.

7. **No feature flags or env-var overlay.** There is no layered config (file → env → DB); the only "layer" is the default constant if the key is absent from `PluginSettings`.

8. **Diagnostics use `confluence.home`.** Both the main plugin's `HomeDirProbe` and the diag-lite module's `HomeDirProbe` read `System.getProperty("confluence.home")` to locate Confluence's home directory for log tailing. This is a diagnostic probe, not application configuration.

## Conventions & Constraints

- Global plugin settings are persisted through `PluginSettingsFactory.createGlobalSettings()` — they are shared across all users and nodes in a Data Center cluster.
- Integer settings are always validated against hard-coded ranges: `sqlTimeoutSeconds ∈ [1, 120]`, `maxResultRows ∈ [100, 500000]`. Values outside these ranges are clamped silently (no exception is thrown).
- The URL whitelist accepts any whitespace-delimited tokens split by newlines, commas, semicolons, or spaces; empty tokens are discarded.
- Admin writes require a valid XSRF security token (`@RequireSecurityToken(true)`), enforced by Confluence's XWork interceptor stack.
- Macro parameters defined in `atlassian-plugin.xml` (e.g. `pageSize`, `chartType`, `sourceType`) are per-macro invocation options, not global configuration — they are declared as `<parameter>` elements with `<value>` enums and defaults, separate from the global `OpenTfcSettings` store.
- No secrets management: the whitelist is stored as plain text in the Confluence database; there is no encryption or secret vault integration.