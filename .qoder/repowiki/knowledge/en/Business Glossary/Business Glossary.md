---
kind: business_term
name: Business Glossary
category: business_term
scope:
    - '**'
---

### OpenTFC
- Definition：Project codename standing for 'Open Table Filter, Charts & Spreadsheets'; the team's open-source replacement for Stiltsoft's Table Filter, Charts and Spreadsheets plugin for Confluence Data Center.
- Aliases：atable_conf、opentfc

### DiagLite
- Definition：Standalone companion plugin (`opentfc-diag-lite.jar`) that exposes `/plugins/servlet/diag-lite` and can diagnose a broken Confluence installation even when the main OpenTFC plugin fails to activate.
- Aliases：diag-lite、diagnostics servlet

### AO entity
- Definition：Active Objects persistent model class registered via the `<ao>` module in `atlassian-plugin.xml`; used here for `FilterProfile`, `SpreadsheetMeta`, and `UserSettings` to store filter profiles and spreadsheet metadata in Confluence's storage layer.
- Aliases：Active Objects entity、AO

### macroId
- Definition：Per-page identifier passed to OpenTFC macros (`table-filter`, `chart`, `pivot`, `spreadsheet`, etc.) that links a rendered instance back to its stored profile/filter state; shared between the source macro and dependent macros (e.g., chart/pivot reference `sourceMacroId`).
- Aliases：macro ID、sourceMacroId

### preset
- Definition：Named saved SQL query configuration for the `opentfc-transformer` macro; allows users to reuse complex DuckDB queries without re-entering them.
- Aliases：SQL preset

### spreadsheet-include
- Definition：Macro that embeds a range or sheet from another page's OpenTFC spreadsheet attachment, enabling cross-page composition of spreadsheet content.
- Aliases：spreadsheet include、open spreadsheet include

### attachment-table
- Definition：Macro that lists a page's attachments as a sortable/filterable table, with optional extension allow/deny lists.
- Aliases：open attachment table
