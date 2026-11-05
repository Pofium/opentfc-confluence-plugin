---
kind: external_dependency
name: Maven + Atlassian Maven Plugin SDK (AMPS)
slug: maven-amps
category: external_dependency
category_hints:
    - vendor_identity
    - framework_behavior
scope:
    - '**'
---

### Identity

### Role in this repo
Builds the `atlassian-plugin` artifact, packages the plugin JAR, generates OBR metadata, and validates the plugin manifest (`mvn verify`). Also hosts the Confluence test server during tests.

### Stable integration shape
- Repositories point to `packages.atlassian.com/maven/repository/public` and `packages.atlassian.com/maven-external/`.
- `mvn test` runs 109 unit tests; `mvn verify` additionally builds and validates the plugin.
- JDK 17 is required (`maven.compiler.release=17`).
- Local toolchain bootstrap uses `.tools/mvn.ps1` and `.mvn/settings-direct.xml`.