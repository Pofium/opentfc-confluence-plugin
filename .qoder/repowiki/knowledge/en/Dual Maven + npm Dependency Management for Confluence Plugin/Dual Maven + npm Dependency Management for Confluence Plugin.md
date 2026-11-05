---
kind: dependency_management
name: Dual Maven + npm Dependency Management for Confluence Plugin
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - package.json
    - package-lock.json
    - .github/workflows/ci.yml
    - .mvn/settings-direct.xml
---

## Approach

The OpenTFC Confluence plugin uses two independent package managers:

- **Maven** (Java backend, AMPS-packaged Atlassian plugin)
- **npm** (TypeScript frontend bundled by webpack)

There is no unified lockfile or shared dependency graph between the two. Each language ecosystem manages its own versions and resolution.

## Java / Maven

### Manifest and central versioning

`pom.xml` declares all dependencies centrally in a `<properties>` block:

| Property | Value | Used by |
|---|---:|---|
| `confluence.version` | 8.5.4 | host platform BOM import |
| `confluence.data.version` | 8.5.4 | AMPS product data version |
| `amps.version` | 8.11.4 | confluence-maven-plugin |
| `atlassian.spring.scanner.version` | 2.2.4 | Spring scanner annotations/runtime |
| `duckdb.version` | 1.1.3 | duckdb_jdbc |
| `poi.version` | 5.2.5 | poi-ooxml |
| `commons-csv.version` | 1.10.0 | commons-csv |
| `jackson.version` | 2.15.3 | jackson-databind/core/annotations |
| `jsoup.version` | 1.17.2 | jsoup |

Runtime dependencies are split into three explicit groups:

1. **Provided by Confluence** — `com.atlassian.confluence:confluence`, `activeobjects-plugin`, `sal-api`, `jsr311-api`, `atlassian-rest-common`, `javax.inject`, `slf4j-api`, `log4j-core`, `javax.servlet-api`, `commons-lang3`, `commons-io`. All declared with `<scope>provided</scope>` so they are not bundled into the plugin JAR.
2. **Bundled into the plugin JAR** — `duckdb_jdbc`, `poi-ooxml`, `commons-csv`, `jackson-databind`, `jsoup` (no scope = compile/runtime).
3. **Test-scoped** — `junit:4.13.2`, `hamcrest:2.2`, `mockito-core:4.11.0`, `slf4j-simple:1.7.36`.

### Platform BOM and transitive pinning

The POM imports `com.atlassian.confluence:confluence-plugins-platform-pom:${confluence.version}` as an imported BOM to align transitive dependency versions with the target Confluence 8.5.4 runtime. It then **overrides** several transitive dependencies that POI pulls in (`commons-io:2.15.1`, `commons-compress:1.26.1`, `commons-codec:1.16.1`, `commons-collections4:4.4`, `xmlbeans:5.2.0`, `log4j-api:2.21.1`) plus the three Jackson artifacts via `<dependencyManagement>`, with a comment explaining the intent: "Pin transitive deps of bundled POI so the platform BOM can't downgrade them".

### Repositories

Two Atlassian-hosted repositories are declared:

- `atlassian-public` → `https://packages.atlassian.com/maven/repository/public`
- `atlassian-external` → `https://packages.atlassian.com/maven-external/`

No private registry, proxy, or mirror is configured in the POM itself. A local `.mvn/settings-direct.xml` exists as a temporary override that clears `<proxies/>` and `<mirrors/>` to bypass a user-level proxy when `packages.atlassian.com` is reachable directly.

### OSGi import policy

The `confluence-maven-plugin` configuration sets `Import-Package` with explicit required packages (Confluence, ActiveObjects, SAL, REST, servlet, SLF4J) and marks optional subsystems (`com.atlassian.plugin.osgi.*`, `org.springframework.*`, `org.apache.logging.log4j.*`) with `resolution:="optional"`. A trailing wildcard `*;resolution:="optional"` is present but commented-out rationale says it previously masked activation wiring errors, so the project moved toward explicit imports for fail-fast behavior.

## Frontend / npm

### Manifest and lockfile

`package.json` declares:

- **Runtime dependencies**: `ag-grid-community ^31.0.3`, `echarts ^5.5.0`, `dayjs ^1.11.10`
- **Dev dependencies**: webpack 5, webpack-cli, ts-loader, typescript, css-loader, mini-css-extract-plugin, style-loader

`package-lock.json` (lockfileVersion 3) pins every transitive dependency with exact versions and SHA integrity hashes, ensuring reproducible builds. The CI workflow runs `npm ci` (not `npm install`), which enforces the lockfile strictly.

### Node toolchain

GitHub Actions (`ci.yml`) provisions Node.js 20 via `actions/setup-node@v4` with `cache: npm`, and JDK 17 Temurin via `actions/setup-java@v4` with `cache: maven`. There is no `.nvmrc` or `.node-version`; the version is pinned only in the workflow.

## Build & CI integration

`.github/workflows/ci.yml` runs the full pipeline on push/PR to `master` and `main`:

1. Checkout
2. Setup JDK 17 (Temurin)
3. Setup Node.js 20
4. `npm ci` (install from lockfile)
5. `npm run check` (tsc type-check)
6. `npm run build` (webpack production bundle)
7. `mvn -B -ntp verify` (backend build + unit tests)

Frontend bundles (`opentfc.bundle.js`, `opentfc.bundle.css`) are emitted under `src/main/resources/js/` and `src/main/resources/css/` respectively and consumed by `atlassian-plugin.xml`.

## Conventions observed

- Java dependency versions live in `<properties>` in `pom.xml`; runtime-only libraries use `<scope>provided</scope>`.
- Transitive dependency conflicts are resolved by overriding them in `<dependencyManagement>` rather than at each `<dependency>` site.
- Host-provided APIs (Confluence, ActiveObjects, SAL, Servlet API, SLF4J) are never bundled.
- Frontend dependencies use caret ranges in `package.json` but are locked down by `package-lock.json` and enforced via `npm ci` in CI.
- No vendored third-party source code exists; both Maven and npm resolve remotely.
- No private Maven/NPM registry is configured in the repository; Atlassian's public packages are used directly.