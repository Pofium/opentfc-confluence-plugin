---
kind: build_system
name: Maven + Webpack Multi-Module Build for Confluence Plugin
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - diag-lite/pom.xml
    - .github/workflows/ci.yml
    - package.json
    - webpack.config.js
    - tsconfig.json
    - .tools/mvn.ps1
    - .tools/settings.xml
---

## Build System Overview

The OpenTFC Confluence plugin uses a **hybrid Maven + Node.js** build orchestrated by GitHub Actions. The repository contains two Maven modules:

- Root module `org.opentfc:opentfc-confluence-plugin` (AMPS-packaged Confluence plugin, `atlassian-plugin` packaging)
- Submodule `diag-lite/` (`org.opentfc:opentfc-diag-lite`, plain JAR companion plugin)

Frontend assets are TypeScript/JS bundled with Webpack and emitted into the main plugin's `src/main/resources/js` and `src/main/resources/css` directories, which Maven then packages.

## Key Files

- `pom.xml` — root Maven POM defining AMPS, dependency versions, OSGi imports, and Java 17 compilation
- `diag-lite/pom.xml` — minimal Maven POM for the standalone diagnostic servlet plugin
- `.github/workflows/ci.yml` — CI pipeline (push/PR on `master` and `main`)
- `package.json` — npm scripts (`build`, `dev`, `check`) and frontend dependencies
- `webpack.config.js` — bundles `index.ts` to `src/main/resources/js/opentfc.bundle.js` (UMD library `OpenTFC`) and CSS to `src/main/resources/css/opentfc.bundle.css`
- `tsconfig.json` — TypeScript target ES2020, strict mode, emits to `src/main/resources/js`
- `.mvn/settings-direct.xml` and `.tools/mvn.ps1` / `.tools/settings.xml` — local toolchain bootstrap (bundled JDK 17.0.20.1+1 and Maven 3.9.9 under `.tools/`)

## Architecture & Conventions

### Dependency Management

- Centralized in root `pom.xml` `<properties>`: `confluence.version=8.5.4`, `amps.version=8.11.4`, `duckdb.version=1.1.3`, `poi.version=5.2.5`, `jackson.version=2.15.3`, `jsoup.version=1.17.2`, etc.
- Host-provided libraries (Confluence, ActiveObjects, SAL, REST API, SLF4J, Servlet API) are declared with `<scope>provided</scope>` so they are not bundled.
- Bundled runtime libraries (DuckDB JDBC, POI OOXML, Commons CSV, Jackson Databind, Jsoup) have no scope and are packaged into the plugin JAR.
- Transitive dependency pinning via `<dependencyManagement>` explicitly pins `commons-io`, `commons-compress`, `commons-codec`, `commons-collections4`, `xmlbeans`, and `log4j-api` to prevent the Atlassian platform BOM from downgrading them (comment: "Pin transitive deps of bundled POI so the platform BOM can't downgrade them").
- Two Maven repositories configured: `atlassian-public` and `atlassian-external`.

### OSGi / Plugin Wiring

- AMPS `confluence-maven-plugin` generates the plugin descriptor; `Atlassian-Plugin-Key` is set to `org.opentfc.confluence.plugin`.
- Explicit `Import-Package` list declares required Confluence/ActiveObjects/SAL/REST packages; optional imports use `resolution:="optional"` for `com.atlassian.plugin.osgi.*`, `org.springframework.*`, and `org.apache.logging.log4j.*`.
- A trailing `*;resolution:="optional"` catches any remaining missing imports rather than failing activation (comment explains this was chosen over a fully explicit list to avoid partial activation states).
- Spring scanning is enabled via `atlassian-spring-scanner-maven-plugin` bound to `process-classes` phase, and `Spring-Context=*` in AMPS instructions.

### Frontend Build Pipeline

- TypeScript source lives under `src/main/frontend/src/`; entry point is `index.ts`.
- Webpack compiles TS via `ts-loader` and extracts CSS via `MiniCssExtractPlugin`.
- Output: `src/main/resources/js/opentfc.bundle.js` (UMD library named `OpenTFC`) and `src/main/resources/css/opentfc.bundle.css`.
- `npm run check` runs `tsc --noEmit` for type-checking; `npm run build` runs webpack in production mode.

### Versioning

- Both Maven modules share version `1.0.4` (root POM line 9, `diag-lite/pom.xml` line 9). There is no parent POM or property sharing the version between modules — each POM hardcodes it independently.

### Local Toolchain

- `.tools/apache-maven-3.9.9/` and `.tools/jdk-17.0.20.1+1/` bundle specific JDK/Maven versions.
- `.tools/mvn.ps1` and `.tools/settings.xml` provide a wrapper for local builds; `.mvn/settings-direct.xml` exists for direct Maven invocation.

## CI Pipeline (`.github/workflows/ci.yml`)

Runs on push/PR to `master` and `main` on `ubuntu-latest`:

1. Checkout code
2. Setup Temurin JDK 17 (with Maven cache)
3. Setup Node.js 20 (with npm cache)
4. `npm ci` — install frontend dependencies
5. `npm run check` — TypeScript type-check
6. `npm run build` — webpack production build
7. `mvn -B -ntp verify` — backend build + unit tests (runs `mvn test` as part of `verify`)

No artifact upload step is present; the workflow validates but does not publish.

## Testing

- Backend tests use JUnit 4.13.2 + Hamcrest 2.2 + Mockito 4.11.0, located under `src/test/java/org/opentfc/`.
- No frontend unit tests are defined in `package.json` scripts; only type checking is enforced.

## Constraints & Rules

- Java source/target is pinned to release 17 (`maven.compiler.release=17`, `maven-compiler-plugin` `<release>17</release>`, CI sets up JDK 17).
- Frontend TypeScript targets ES2020 with `strict: true` and `skipLibCheck: true`.
- CI requires both Node.js 20 and JDK 17 to succeed.
- The `diag-lite` module deliberately avoids the heavy `confluence` artifact and instead depends only on narrow APIs (`javax.servlet-api`, `atlassian-plugins-core`, `sal-api`, `atlassian-event`, `log4j-core`), all `provided`, producing a ~25 KB JAR that can activate even when the main plugin fails (documented in `diag-lite/pom.xml` comment).
- AMPS `skipBannedDependencies=true` is set, allowing non-standard dependencies while still using AMPS packaging.