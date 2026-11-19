# Test strategy and commands

## Automated suites
- Backend units: invalid sorting rejected before DB access, not-found behavior,
  signing-key format/minimum strength.
- PostgreSQL integration: Flyway/schema, explicit bootstrap, CRUD and audit atomicity,
  duplicate normalization, field validation, all sort fields, search/filter/pagination,
  malformed/unknown JSON, page/type guards, RBAC including metrics, login throttling,
  disabled users, JWT expiry/audience/signature, role/password revocation, password hashing
  and Unicode byte limits, self-admin safety, stale updates/deletes, truly concurrent edits,
  bounded bodies, framework HTTP errors and release version.
- Desktop units: endpoint policy, loopback opt-in, ProblemDetail parsing.
- JavaFX: real toolkit/CSS/layout, login validation, screenshots under Xvfb. With live
  env variables, the same suite signs in, creates/edits/deletes via UI and signs out.

The full Maven command executes **30 tests** when RUN_FX_TESTS=true. Without the flag,
the one GUI test is skipped intentionally. No H2 fallback is used.

## Default (Docker/Testcontainers)
```sh
RUN_FX_TESTS=true xvfb-run -a ./mvnw -B clean verify spotless:check
```
On macOS/Windows a graphical session replaces Xvfb; set RUN_FX_TESTS=true in the
shell environment. Testcontainers requires a usable Docker daemon. CI uses Docker
on Ubuntu. Tests named *IT run in Maven Failsafe during `verify`, **not just `test`**.

## Docker-free PostgreSQL
**Destructive**: test setup truncates app_users/employees/audit_events/login_attempts.
Only use a dedicated disposable database whose name ends in `_test`; an explicit
safety check rejects other names. Never point tests at production or a real directory.
```sh
export TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/staffdesk_test
export TEST_DB_USER=<test-owner>
export TEST_DB_PASSWORD=<test-secret>
RUN_FX_TESTS=true xvfb-run -a ./mvnw -B verify spotless:check
```
The test owner must be allowed to install pg_trgm. Without TEST_DB_URL, a fresh
PostgreSQL 17 container is used and Flyway runs against it.

## Real desktop -> API -> DB workflow
Start a separately configured local backend with a dedicated empty employee table
and known test admin. Avoid concurrent modifications during the workflow. Run:
```sh
RUN_FX_TESTS=true FX_E2E_URL=http://127.0.0.1:8080 \
FX_E2E_EMAIL=<test-admin-email> FX_E2E_PASSWORD=<test-admin-secret> \
xvfb-run -a ./mvnw -B -pl desktop -am test
```
It creates `asha.ui@example.test`, edits job title, confirms hard deletion and signs out.
Desktop tests require it to be the only employee for deterministic row assertions.
It uses the production ApiClient (no mock HTTP) and renders the real JavaFX UI.
Screenshots: `desktop/target/screenshots/`. The CI container job runs this against Compose.

## API smoke
`API_URL=<origin> python3 scripts/smoke-api.py` checks readiness and denied anonymous
access. Add SMOKE_EMAIL and SMOKE_PASSWORD for credentialed CRUD; the test employee
is deleted in a finally block. Enforce HTTPS on real deployments; ALLOW_LOCAL_HTTP=true
only permits loopback development. Never use `curl -k` to claim a verified deployment.

## Evidence and limits
Maven reports are in module target/surefire-reports and backend/target/failsafe-reports;
selected sanitized report summaries are checked into docs/evidence for this delivery.
See VERIFICATION.md for what was actually run. CI YAML existing in a repository is
not evidence that hosted CI has passed. A Linux build does not prove macOS/Windows
runtime behavior. Full load tests, fuzzing and external penetration tests remain release gates.
