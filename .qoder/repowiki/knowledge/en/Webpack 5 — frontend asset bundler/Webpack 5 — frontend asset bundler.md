---
kind: external_dependency
name: Webpack 5 — frontend asset bundler
slug: webpack
category: external_dependency
category_hints:
    - framework_behavior
scope:
    - '**'
---

### Identity

### Role in this repo
Builds `src/main/frontend/src/index.ts` into `src/main/resources/js/opentfc.bundle.js` and CSS into `src/main/resources/css/opentfc.bundle.css`, which are then served by Confluence WRM.

### Stable integration shape
- `npm run build` → production bundle; `npm run dev` → watch mode.
- TypeScript compilation is separate via `npm run check` (`tsc --noEmit`).
- Bundle output paths are consumed by `atlassian-plugin.xml` WRM resource declarations — changing them requires updating both webpack config and the plugin descriptor.