# Security review and threat model

## Trust boundaries and controls
1. Untrusted native client/network: HTTPS required by ApiClient except explicit literal
   loopback opt-in. Origin-only URLs, no embedded credentials, no redirects or TLS bypass.
   No server secrets in desktop; JWT stays in memory. A compromised desktop OS remains
   able to inspect its process; desktop software cannot prevent that.
2. Authentication boundary: BCrypt cost 12; per-user salts inside hashes. 12–72 character
   password inputs with an additional 72 UTF-8-byte bound to prevent BCrypt truncation.
   Generic login errors and dummy hash verification for unknown accounts. No public signup.
   DB-backed atomic fixed-window identity throttling across replicas; edge global/IP
   throttling still required. No refresh token or token cache on disk.
3. JWT boundary: pinned HS256, >=256-bit decoded Base64 key, issuer/audience/timestamps,
   account enabled/role/token_version checked every request. Admin role/password changes
   revoke existing tokens immediately. Signing key is server-only. The default Spring
   timestamp validator permits a small clock skew; keep hosts synchronized with NTP.
4. Authorization: server-side method-level role checks; hidden UI buttons are not security.
   No cookie auth, no server session, CSRF disabled for bearer-only API. CORS not enabled.
5. Data boundary: immutable validated DTOs, unknown JSON properties rejected, no JPA entity
   serialization, prepared queries, sort allowlist, escaped wildcard search, page/size caps,
   16 KiB JSON body bound including chunked uploads, no arbitrary SQL/filter expression API.
6. Mutation integrity: PostgreSQL unique constraints, transactional audit insertion,
   JPA version checks for conflicting updates/deletes, serialized administrator invariants.
7. Operational boundary: non-root backend, read-only root filesystem and dropped caps in
   Compose, private DB networking, environment secrets, public health without details,
   request IDs, structured sanitized logging, production docs off by default.

No hardcoded production credentials are included. Test-only keys/passwords are visibly
scoped to tests/local evidence. Sample .env entries are not usable defaults. Login
passwords/tokens do not appear in application request logs. Integration framework
failure reports can include test tokens and must not be reused for production traffic.

## Issues found during implementation and addressed
- Default framework ProblemDetail advice hid custom field validation: explicit precedence fixed.
- Loopback development could otherwise encourage unsafe remote HTTP: enforced URI-origin policy.
- Chunked uploads could bypass Content-Length bounds: read bounded request bytes before dispatch.
- Native query sort injection: map API field names to a hardcoded column allowlist.
- Stale desktop responses could replace newer search results: request/session generation checks.
- Re-entering directory could accumulate listeners: register the debounced search listener once.
- Password Unicode length could exceed BCrypt bytes: enforce byte limit at all creation/reset/login paths.
- Account state changes could leave usable old tokens: validate current DB token_version/role.
- Concurrent admin operations could remove the last admin: transaction advisory lock + invariant checks.
- Health-only CI verification could accept old release: wait for exact public commit revision.
- Duplicate-constraint DB logging could expose emails: disable Hibernate SQL exception detail logger.

## Remaining production gates and limitations
- Actual hosting/TLS, provider firewall/edge rate limits, alert delivery and external network
  access have not been provisioned or verified. Render internal DB TLS is an explicitly
  documented self-signed private-network exception; not suitable over untrusted networks.
- Container vulnerability scan and cross-OS CI jobs are configured but not run here because
  there is no Docker daemon or Windows/macOS runner in this sandbox. Resolve every scan
  result, pin reviewed actions/image digests, and sign/notarize release packages.
- Initial local/blueprint DB login is the schema owner for automatic Flyway bootstrap.
  Before sensitive production use, create a migration-owner identity and separate runtime
  DML identity; execute migrations in a protected job, disable startup Flyway on API, grant
  only SELECT/INSERT/UPDATE/DELETE on needed tables and USAGE/SELECT on audit sequence.
  Use dedicated backup identity. Do not grant the runtime role CREATE/DROP/CREATEROLE.
- All enabled directory users can see all employee contact fields. Row-level tenancy,
  field-level privacy, MFA, SSO/OIDC, device binding, breached-password checks, self-service
  recovery, refresh-token rotation and retention automation are outside this release.
- Signing out only clears the local bearer token. A stolen token survives until expiry,
  key rotation or an administrator account change; there is no per-session denylist.
- Per-identity throttling can be abused for account lockout/random-identity table growth;
  configure IP/global limits at trusted ingress before public release. Counter cleanup exists.
- Monitoring currently requires administrator auth; prefer provider-native monitoring until
  a dedicated metrics identity/private management port is added. Never store an unrotated
  privileged token in a public Prometheus config.
- Load/capacity testing and external penetration testing are not claimed. Offset pagination,
  exact counts, unrestricted distinct-department metadata and one account read per request
  are deliberate scale trade-offs; benchmark representative business volume before sizing.

## Runtime privilege example (DBA runbook, not an automatic migration)
Create a runtime login using your secrets tool, then as schema owner:
```sql
GRANT CONNECT ON DATABASE staffdesk TO staffdesk_runtime;
GRANT USAGE ON SCHEMA public TO staffdesk_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON employees, app_users, audit_events, login_attempts TO staffdesk_runtime;
GRANT USAGE, SELECT ON SEQUENCE audit_events_id_seq TO staffdesk_runtime;
GRANT SELECT ON flyway_schema_history TO staffdesk_runtime;
```
Remove unnecessary schema PUBLIC CREATE rights after reviewing other applications.
JPA schema validation needs metadata visibility. Future migrations must extend grants
as needed. Run API with DB_USER=staffdesk_runtime and FLYWAY_ENABLED=false **only
after** a protected migration job has applied the expected schema.

Report vulnerabilities privately through your organization’s established security
channel. No fictitious support mailbox or compliance certification is provided.
