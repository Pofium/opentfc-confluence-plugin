---
kind: external_dependency
name: Atlassian Confluence Data Center / Server 8.5 LTS – 9.x
slug: atlassian-confluence
category: external_dependency
category_hints:
    - vendor_identity
    - client_constraint
scope:
    - '**'
---

### Identity

### Role in this repo
Host runtime providing OSGi, REST, XWork, Active Objects, AUI web resources, servlet container, and log4j. All host-provided APIs (`com.atlassian.confluence.*`, `activeobjects-plugin`, `sal-api`, `jsr311-api`, `atlassian-rest-common`, `javax.servlet-api`, `log4j-core`) are declared `provided` so they are not bundled into the plugin JAR.

### Integration shape
- Plugin descriptor `atlassian-plugin.xml` registers macros, REST API (`/rest/opentfc` v1.0), admin XWork actions, web resources, AO entities, and the diagnostics servlet.
- AMPS (`com.atlassian.maven.plugins:confluence-maven-plugin:${amps.version}`) drives packaging; `mvn verify` produces the P2 artifact and validates the manifest.
- Frontend assets are loaded via WRM contexts `viewcontent`, `preview`, `editor`.

### Stable constraints
- Must run inside Confluence DC/Server 8.5 LTS – 9.x; cannot be deployed standalone.
- Runtime classes from Confluence must remain absent from the plugin classpath (enforced by `provided` scope and explicit `Import-Package` list).
- Verify exact Confluence API surface against the version pinned in `pom.xml` before upgrading.