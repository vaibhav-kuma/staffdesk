# Hosted deployment and release checklist

## Current state
**Not deployed. Public API URL: not provisioned / not verified.** The user explicitly
selected “Build first.” Nothing in this repository represents an active paid hosting
resource. The included blueprint is a deployment template, not proof of deployment.
A Docker sandbox listener is not a reliable production endpoint.

## Render deployment
1. Push this repository to your Git host, keep `.env` and private keys out of it.
2. Create a Render account/workspace with billing enabled. Create a Blueprint from
   this repository’s root `render.yaml`. Inspect the provider plan names/prices in
   the account before applying (platform offerings can change). Template places
   service + PostgreSQL 17 in Singapore; change both together if needed.
3. Supply BOOTSTRAP_ADMIN_EMAIL and a unique long BOOTSTRAP_ADMIN_PASSWORD.
   JWT_SECRET is generated as a Base64 256-bit value by Render; do not copy it to clients.
4. Blueprint uses paid compute, persistent managed PostgreSQL, private DB networking,
   no external DB allowlist entries, provider HTTPS ingress and a readiness route.
   The container binds to 0.0.0.0 by default; `PORT=8080` is explicit. It derives a
   JDBC URL from DB host/name/port instead of misusing Render’s `postgresql://` URL.
5. Ensure `pg_trgm` is permitted/installed. On first startup Flyway migrates and JPA
   validates. A failed migration/readiness check must block traffic. Never bypass it.
6. Copy the **actual assigned public HTTPS origin** from the provider dashboard.
   A name such as `staffdesk-api` does not guarantee a particular public hostname.
7. Verify with `API_URL=<actual-origin> python3 scripts/smoke-api.py`. Then provide
   SMOKE_EMAIL/SMOKE_PASSWORD privately for a CRUD smoke round trip. Check desktop
   login and CRUD from a separate network/OS, including its trust store.
8. Record real URL, deployment ID, Git SHA, timestamp, region, plan and verification
   output in docs/VERIFICATION.md. Only now describe the URL as working.
9. Remove the bootstrap variables and redeploy. Confirm existing login still works.
10. Configure monitoring, retention/backups, edge rate limits and an approved restore
    drill, then complete the security gates below.

The private Render PostgreSQL endpoint only supports self-signed TLS; `sslmode=require`
is a conscious encrypted-private-network exception, not a public TLS bypass. External
DB access is disabled in the blueprint. For an alternative database reachable across
an untrusted network use `sslmode=verify-full`, a hostname-matching certificate and
trusted CA (`sslrootcert` or the JDBC default Java SSL factory). Never use trust-all
JavaFX HTTPS clients or public PostgreSQL exposed to every desktop.

Provider references used when preparing the template:
- https://render.com/docs/blueprint-spec
- https://render.com/docs/postgresql-creating-connecting
- https://render.com/docs/deploy-hooks
- https://render.com/docs/postgresql-backups

## CI/CD
The GitHub workflow:
1. Maven verifies units, PostgreSQL Testcontainers integration and JavaFX rendering;
   checks Java formatting; produces SBOM and test evidence.
2. Builds Docker image, scans OS/JVM dependencies for HIGH/CRITICAL vulnerabilities,
   validates Compose and runs authenticated smoke + real JavaFX CRUD against containers.
3. Builds OS-native portable app-images on Linux, Windows, macOS (no cross-compilation).
4. Optionally deploys after all jobs pass on main using the protected `production` environment.

Configure repository/environment variable `DEPLOY_ENABLED=true`, variable
`PRODUCTION_API_URL` to the actual assigned HTTPS origin and secret
`RENDER_DEPLOY_HOOK` from the Render service settings. The deploy script pins the
hook to **GITHUB_SHA**, waits for public `/api/v1/version` to return that exact SHA,
then verifies readiness. Checking readiness alone could accidentally accept an old
healthy release. `RENDER_GIT_COMMIT` supplies the version inside Render. Run the
credentialed smoke test as a separate protected release step with short-lived secrets.

Disable Render’s independent auto-deploys (`autoDeployTrigger: off` in blueprint)
to avoid bypassing CI. Protect main, require checks/reviews, restrict production
approval, and pin GitHub Actions/base images to reviewed immutable SHAs/digests
for regulated production. Dependabot is enabled but not a substitute for approval.

Hook mode rebuilds from the tested commit on Render; it is not byte-identical image
promotion. For reproducible artifact promotion, publish the CI-scanned image to a
private registry, deploy its digest via Render’s image-backed API/hook, and verify the
same revision. Provisioning registry credentials/API access is account-specific.

## Self-hosting
Compose is **local-only** intentionally. For a public VM/container host put the API
behind a maintained TLS proxy/load balancer, restrict direct backend ingress, enable
trusted forwarded headers only there, configure HSTS and automatic cert renewal,
rate-limit login by IP/global requests, cap all request bodies and enforce timeouts.
Back up the PostgreSQL volume off-host. Do not publish port 5432. Prefer managed
PostgreSQL for production; a Compose named volume alone is not a backup.

## Release gates
- [ ] Actual account resources provisioned; URL and certificate verified externally.
- [ ] CI Docker/security/native jobs pass on this repository and exact commit.
- [ ] Client smoke tests on real Windows, Linux, macOS; organization signing/notarization.
- [ ] Role reviews, second administrator, password policy and secret rotation owner.
- [ ] Reverse-proxy IP/global throttling and abuse protection enabled.
- [ ] Database least-privilege migration/runtime separation reviewed (see SECURITY.md).
- [ ] Managed DB backup/PITR plan confirmed, off-site encrypted backup scheduled.
- [ ] Successful timed restore drill on disposable DB; RPO/RTO agreed and recorded.
- [ ] Alerts delivered to a real on-call destination; incident/rollback drill completed.
- [ ] Representative data volume/load test; connection/memory sizing and extension review.

## Scaling, rollback, migrations
JWT verification is stateless except for DB-backed account revocation; replicas share
rate counters and PostgreSQL. Keep pool_size × max_replicas below database capacity
with headroom for migrations/backups. Use a pooler if necessary; advisory transaction
locks work with transaction pooling. Audit rows share mutation transactions.

Never overwrite a JAR while its JVM is running; publish immutable versioned files or images.
Use forward-compatible expand/contract SQL. Flyway takes a migration lock, but large
production migrations should be scheduled separately. After a DBA-managed migration,
run the API with FLYWAY_ENABLED=false and a restricted runtime DB user; JPA still
validates. Never grant runtime DROP/CREATE for convenience in a high-security environment.

If a release fails, redeploy the previous known-good commit/image only if its schema
is compatible. Do not automatically run down-migrations. Restore into a new database
and switch the endpoint during a reviewed incident when rollback needs data recovery.
