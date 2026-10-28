# Changelog

All notable changes to OpenTFC are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-10-28

### Added
- **Браузерная диагностика** `/plugins/servlet/opentfc/diagnostics`: in-memory ring buffer (2000 записей),
  события plugin framework (ENABLED/DISABLED всех приложений — виден каскад отключений),
  перехват WARN/ERROR host-лога через log4j2-аппендер (guarded, опционально),
  события OpenTFC (Spring-контекст, первый рендер макросов, SQL-запросы, ошибки источников REST),
  фильтры уровней, авто-обновление, скачивание `.log`, очистка. Страница — чистый HTML сервлета
  без velocity-декораторов/AUI, доступна даже на инстансе со сломанным рендерингом.
- `PluginLifecycleLogger` — Spring-bean, публикующий события plugin framework в буфер.
- Unit test suite for backend services (109 tests, JaCoCo line coverage 94 %, branch 78 %):
  - `TableExtractorImpl`: headerless tables, column type inference (number/date/select),
    HTML entities, missing-cell padding.
  - `DataSourceResolver`: CSV separators and cp1251, JSON array-of-objects and array-of-arrays,
    SSRF guard (localhost/RFC1918/link-local/any-local/non-HTTP protocols).
  - `SqlTransformerEngine`: JOIN, GROUP BY, UNION, window functions, quoted identifiers
    with spaces, sandbox keyword scan with comment/literal stripping (no false positives).
  - `ExportService`: CSV quoting, XLSX round-trip verified through Apache POI.
  - Macro render tests: parameter mapping into `data-opentfc-config`, HTML escaping (XSS),
    static PDF/Word/email export fallback, JSON source, spreadsheet include, attachment
    listing (metadata, include/exclude filters, size formatting).
  - REST layer: `/source` (attachments, CSV/JSON, SSRF), `/transformer`, `/export`, `/config`,
    `/profile` (create/update/delete plus unauthenticated 401 guards, foreign-profile 404).
  - Admin action: default limits, save/load round-trip, bad stored values.
  - i18n consistency: all descriptor keys present in ru/en, identical key sets, real translation.
  - Model JSON contract used between backend and browser bundle.
- JaCoCo coverage report (`mvn test` → `target/site/jacoco/index.html`).
- CI workflow (GitHub Actions): JDK 17 + Node 20, frontend tsc/webpack, Maven `verify`.
- `LICENSE` (Apache-2.0), `CHANGELOG.md`.

### Fixed
- **Причина поломки инстанса при установке v1.0.0 (инцидент 27–28.10.2026)**:
  - `atlassian-plugin.xml`: web-resource больше не инжектится в контекст `atl.general`
    (1 МБ JS/CSS грузился на каждой странице инстанса, ломая интерактив при ошибке);
  - удалены конфликтующие алиасы макросов без префикса `open-*` (`table-filter`,
    `chart`, `pivot`, `csv`, `json`, `chart-from-table`, `pivot-table`, `table-transformer`,
    `table-spreadsheet`, `attachment-table` и др.) — дубли со встроенным Chart-макросом
    Confluence и макросами сторонних приложений срывали активацию;
  - `pom.xml`: явные обязательные `Import-Package` вместо `*;resolution:="optional"` для
    хостовых API (fail-fast на ошибках wiring); optional оставлены только для
    `org.springframework.*` и `org.apache.logging.log4j.*` (гардируются в рантайме).
- i18n: missing `opentfc.admin.title` key referenced by the plugin descriptor's admin web-item.
- SQL sandbox: queries containing keywords inside string literals, comments or quoted
  identifiers are no longer falsely rejected (e.g. `SELECT 'DROP' AS msg FROM T1`).
- Table extraction: when a table has no `<thead>`, the row used to derive headers is no longer
  emitted as a data row (Jsoup auto-wraps rows in a synthetic `<tbody>`).
- CSV parsing: column order now follows the file (`getHeaderNames()`) instead of a HashMap,
  and JSON array-of-arrays is supported (positional `Column N` headers).
- Attachment table: extensionless files no longer slip through the `includeExtensions` filter,
  and file sizes are formatted with `Locale.ROOT` (no decimal comma under ru_RU).

## [1.0.0-SNAPSHOT] - 2026-10-26

### Added
- P2 plugin skeleton for Confluence DC/Server 8.5–9.x (Java 17, Atlassian SDK).
- Table Filter macro (`opentfc-table-filter`) with interactive filtering, multi-sort,
  frozen header/columns, totals, pagination and CSV/XLSX export.
- Chart from Table macro (`opentfc-chart`) with 15+ ECharts types including Gantt.
- Pivot Table macro (`opentfc-pivot`) with multi-field aggregation.
- Table Transformer macro (`opentfc-transformer`) with in-memory DuckDB SQL sandbox.
- Table Spreadsheet macro (`opentfc-spreadsheet`) + Spreadsheet Include (`opentfc-spreadsheet-include`).
- Attachment Table macro (`opentfc-attachment-table`).
- CSV / JSON source macros (`opentfc-csv`, `opentfc-json`) with SSRF-protected URL fetching.
- REST API v1 (`/rest/opentfc/1.0`): source, transformer, profile CRUD, export, config.
- Active Objects entities: `FilterProfile`, `SpreadsheetMeta`, `UserSettings`.
- Admin configuration page (SQL timeout, result row limit, URL whitelist).
- i18n resources (ru/en).
- Installation & acceptance guide (`INSTALL.md`).
