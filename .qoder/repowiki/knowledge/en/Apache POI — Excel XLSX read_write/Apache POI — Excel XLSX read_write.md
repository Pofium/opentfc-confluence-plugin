---
kind: external_dependency
name: Apache POI — Excel XLSX read/write
slug: apache-poi
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Identity

### Role in this repo
Reads and writes `.xlsx` spreadsheets: exporting filtered tables and persisting OpenTFC spreadsheet content as page attachments with Confluence versioning.

### Stable usage model
- Only OOXML (`poi-ooxml`) path is used; legacy HSSF/XSSF-only APIs are not referenced here.