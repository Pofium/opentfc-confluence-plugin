---
kind: external_dependency
name: AG Grid Community — table/grid widget
slug: ag-grid-community
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Identity

### Role in this repo
Renders the interactive table behind the `opentfc-table-filter` macro (sorting, filtering, pagination, column hiding, totals row, sticky headers).

### Stable notes
- Community edition is used (no Enterprise features); keep it aligned with the license constraint stated in README.md.
- Tree-shaking is recommended to reduce the ~1 MiB bundle size noted in the analysis report.