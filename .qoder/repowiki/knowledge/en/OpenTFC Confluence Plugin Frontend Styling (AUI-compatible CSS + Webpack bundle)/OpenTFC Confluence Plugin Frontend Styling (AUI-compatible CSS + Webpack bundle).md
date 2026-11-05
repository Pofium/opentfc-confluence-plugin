---
kind: frontend_style
name: OpenTFC Confluence Plugin Frontend Styling (AUI-compatible CSS + Webpack bundle)
category: frontend_style
scope:
    - '**'
source_files:
    - src/main/frontend/src/css/opentfc.css
    - webpack.config.js
    - package.json
---

## What system/approach is used

The OpenTFC Confluence plugin ships a small, self-contained frontend built with **TypeScript + Webpack** that emits a UMD library (`OpenTFC`) and a single CSS bundle. There is no component framework (no React/Vue/Svelte), no CSS-in-JS, no design-token system beyond CSS custom properties, and no responsive/mobile-first strategy — the UI targets Confluence Data Center pages inline.

Styling is done in a single hand-authored stylesheet at `src/main/frontend/src/css/opentfc.css` (502 lines). It uses:
- The **Atlassian Design System CSS custom properties** (`--ds-text`, `--ds-surface`, `--ds-border`, `--ds-surface-sunken`, `--ds-surface-hovered`, `--ds-border-focused`, `--ds-background-brand-bold`, `--ds-background-brand-bold-hovered`) so colors adapt to the host Confluence theme.
- A flat class naming scheme: every selector is prefixed with `opentfc-` (e.g. `.opentfc-toolbar`, `.opentfc-btn`, `.opentfc-table-wrapper`, `.opentfc-modal-overlay`).
- Vanilla CSS modules are not used; styles are bundled into one file via `mini-css-extract-plugin` and emitted as `src/main/resources/css/opentfc.bundle.css`.

The only runtime dependencies with visual impact are:
- `ag-grid-community` ^31.0.3 (table rendering)
- `echarts` ^5.5.0 (chart rendering)
- `dayjs` ^1.11.10 (date formatting — no styling)

No Tailwind, Sass/Less, PostCSS, or CSS-in-JS tooling is present.

## Key files and packages

- `src/main/frontend/src/css/opentfc.css` — the sole stylesheet defining all UI components (toolbar, buttons, dropdowns, multiselect panel, table wrapper with sticky headers/frozen columns, pagination bar, modal dialog, chart/pivot/sheet viewport, badges).
- `webpack.config.js` — builds TypeScript entry `./src/main/frontend/src/index.ts` into `src/main/resources/js/opentfc.bundle.js` (UMD, global name `OpenTFC`) and extracts CSS into `src/main/resources/css/opentfc.bundle.css`.
- `package.json` — declares the build scripts (`build`, `dev`, `check`) and the three runtime deps (`ag-grid-community`, `echarts`, `dayjs`).
- `tsconfig.json` — TypeScript configuration for the frontend source tree.

## Architecture and conventions

**Component-like CSS classes.** Each UI concept is represented by a BEM-style flat class under the `opentfc-` namespace:
- Layout containers: `.opentfc-container`, `.opentfc-toolbar` / `.opentfc-toolbar-primary` / `.opentfc-toolbar-secondary`, `.opentfc-table-wrapper`, `.opentfc-pagination-bar`.
- Controls: `.opentfc-input`, `.opentfc-input-compact`, `.opentfc-input-num`, `.opentfc-input-date`, `.opentfc-input-txt`, `.opentfc-btn`, `.opentfc-btn-primary`, `.opentfc-btn-sm`, `.opentfc-btn-reset`.
- Popovers: `.opentfc-dropdown`, `.opentfc-dropdown-menu`, `.opentfc-dropdown-item`, `.opentfc-multiselect-panel`, `.opentfc-multiselect-options`, `.opentfc-multiselect-option`.
- Table internals: `.opentfc-sticky-header th`, `.opentfc-frozen-cell`, `.opentfc-totals-row`, `.opentfc-sort-badge`, `.opentfc-sort-priority`.
- Modal: `.opentfc-modal-overlay`, `.opentfc-modal-box`, `.opentfc-modal-title`, `.opentfc-modal-actions`.
- Shared canvas wrappers: `.opentfc-chart-canvas`, `.opentfc-pivot-grid`, `.opentfc-sheet-viewport`.

**Color & token usage.** Colors come from Atlassian DS CSS variables with hard-coded fallbacks (e.g. `var(--ds-text, #172b4d)`, `var(--ds-surface, #f4f5f7)`). Where no DS variable exists, Confluence's default palette is used directly (`#172b4d` text, `#0052cc` brand blue, `#6b778c` secondary text, `#dfe1e6` borders, `#ebecf0` hover surfaces).

**Typography.** Font stack is set on `.opentfc-container`: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, "Fira Sans", "Droid Sans", "Helvetica Neue", sans-serif`. Base font size is 13px; compact inputs use 12px; badges and sort indicators use 11px/10px.

**Interaction states.** Hover/active/focus are defined per-component using standard pseudo-selectors (`.opentfc-btn:hover`, `.opentfc-btn:active`, `.opentfc-input:focus`). Transitions are applied sparingly (border-color, box-shadow, background, color over 0.15–0.2s).

**Z-index layering.** Explicit z-index values are used for stacking contexts: dropdown menus and multiselect panels at `z-index: 1000`, sticky headers at `z-index: 10`, frozen cells at `z-index: 15` (header) / `z-index: 5` (cells), modal overlay at `z-index: 2000`.

**Table behavior.** The table wrapper forces `border-collapse: separate !important` and `border-spacing: 0`, uses `overflow: auto` with a fixed `max-height: 650px`, and implements sticky column headers plus horizontally scrollable frozen columns via `position: sticky`.

**Build output contract.** Webpack emits two artifacts consumed by the Java side of the plugin:
- JS: `src/main/resources/js/opentfc.bundle.js` (UMD, global `OpenTFC`)
- CSS: `src/main/resources/css/opentfc.bundle.css`
The CSS filename pattern `[name].bundle.css` is configured in `MiniCssExtractPlugin`.

## Conventions and constraints

- All style selectors use the `opentfc-` prefix to avoid leaking into or being affected by Confluence page styles. This is an observed convention across the entire stylesheet.
- Visual tokens are sourced from Atlassian Design System CSS custom properties (`--ds-*`) with explicit fallback hex values, ensuring the UI remains usable even if the host page does not define those variables.
- No scoped CSS, CSS Modules, Shadow DOM, or component-scoped stylesheets are used; everything is global within the bundle.
- No responsive breakpoints or media queries exist in the stylesheet — the layout assumes desktop Confluence pages.
- The build pipeline is fixed: `npm run build` runs `webpack --mode production`; `npm run dev` runs `webpack --mode development --watch`; `npm run check` runs `tsc --noEmit` for type checking.
- Only one CSS entry point is supported by the Webpack config (`entry.opentfc`); adding more bundles would require editing `webpack.config.js`.
- The stylesheet is intentionally kept as a single file rather than split into partials — there are no SCSS imports, CSS imports between files, or modular CSS architecture.