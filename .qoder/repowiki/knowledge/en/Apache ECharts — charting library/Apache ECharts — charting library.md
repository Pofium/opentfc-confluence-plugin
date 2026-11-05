---
kind: external_dependency
name: Apache ECharts — charting library
slug: apache-echarts
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Identity

### Role in this repo
Renders the 15+ chart types exposed by the `opentfc-chart` macro (column, bar, line, area, pie, donut, scatter, radar, funnel, gantt) and supports export to PNG/SVG.

### Stable notes
- Largest single contributor to the JS bundle (~800 KiB); consider dynamic import or separate chunk to avoid loading charts on every page.
- MIT-licensed, consistent with the project's Apache-2.0 licensing claim.