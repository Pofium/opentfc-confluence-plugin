---
kind: external_dependency
name: Day.js — lightweight date library
slug: dayjs
category: external_dependency
category_hints:
    - vendor_identity
scope:
    - '**'
---

### Identity

### Role in this repo
Date parsing/formatting for spreadsheet cells and filter UI. Chosen over full-size moment.js to keep the bundle small.

### Stable notes
- No plugins are imported in this repo; if new functionality requires plugins (timezone, locale, etc.), add them explicitly rather than importing the full dayjs package.