---
kind: external_dependency
name: DuckDB embedded SQL engine (JDBC driver)
slug: duckdb-jdbc
category: external_dependency
category_hints:
    - vendor_identity
    - framework_behavior
scope:
    - '**'
---

### Identity

### Role in this repo
In-memory SQL engine powering the `opentfc-transformer` macro. Queries run against an isolated DuckDB instance per request; DDL/DML are blocked, only SELECT is allowed, and `;` is rejected to prevent multi-statement injection.

### Stable integration shape
- Sandboxed execution: `enable_external_access=false`, `lock_configuration=true`, timeout enforced.
- External access is further guarded by SSRF checks (protocol whitelist + loopback/site-local/link-local/multicast IP block).
- JDBC URL and prepared statements are used through `SqlTransformerEngine`; do not pass raw user SQL directly to the DB — always go through the transformer pipeline.