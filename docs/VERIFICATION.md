# Verification and delivery report

**19 November 2026. Build-first scope. Public production URL: NOT PROVISIONED / NOT VERIFIED.**

## Tested environment
Linux x86_64, OpenJDK 21.0.13, Maven Wrapper 3.9.11, PostgreSQL 17.12, JavaFX 21.0.13,
Spring Boot 3.5.16. PostgreSQL was installed directly because this sandbox has no
Docker daemon. Integration tests used an explicitly disposable `*_test` database;
the source defaults to Testcontainers when those variables are not supplied.

## Completed verification
| Check | Result | Evidence |
|---|---|---|
| All Maven modules compile/package | PASS | evidence/maven-final-summary.txt |
| Backend unit tests | 3 passed | evidence/test-summary.json |
| Real PostgreSQL integration tests | 23 passed | evidence/test-summary.json |
| Desktop client units | 3 passed | evidence/test-summary.json |
| Native JavaFX rendering/validation test | 1 passed | evidence/test-summary.json |
| **Unique automated tests total** | **30 passed; 0 failures/errors/skips** | evidence/test-summary.json |
| Additional live JavaFX UI -> HTTP -> DB workflow | PASS | evidence/desktop-e2e-summary.txt |
| HTTP login + create/read/update/search/delete | PASS (local HTTP only) | evidence/local-http-smoke.txt |
| Persisted employee/account after backend stop/restart | PASS | evidence/restart-persistence.txt |
| Encrypted pg_dump + checksum + restore into separate DB | PASS | evidence/backup-restore.txt |
| Restored DB login, payload and authenticated CRUD smoke | PASS | evidence/restored-database-check.txt |
| Linux jpackage app-image, bundled-runtime launch | PASS | evidence/native-package.txt |
| Swagger index/assets + OpenAPI JSON | PASS (resources fully read) | docs/openapi.json |
| Production-profile structured logs / graceful shutdown | PASS on local JVM | Observed during final real HTTP run |
| Source Java formatting, shell syntax, Python compile, YAML parse | PASS | Local commands run during implementation |
| CycloneDX SBOM | Generated, 113 components | docs/sbom.json |

The additional desktop workflow runs the same desktop test suite against a real
running backend. It is not counted as four new distinct tests. It exercises sign-in,
create, edit, confirmed deletion and sign-out. Screenshots in docs/screenshots are
real JavaFX renderings, not a browser mockup or generated illustration.

The API was restarted without bootstrap credentials, verified its previously persisted
record, then cleaned up verification records. Backup/restore copied a real employee,
account hash and audit/schema data; the restored backend passed login and CRUD. This
is a local functional restore check, not a claim of production RPO/RTO or off-site durability.

## Configured but NOT executed/provisioned here
- Docker image build, Compose runtime and Trivy container/Java vulnerability gate.
- Hosted GitHub Actions pipeline; repository and account access are not connected.
- Native app-image packaging/runtime tests on Windows/macOS; code signing/notarization.
- Render account resources, managed database, custom/public API URL and real HTTPS certificates.
- External monitoring delivery, scheduled off-site backups and provider PITR configuration.
- Representative load/capacity benchmarks and independent penetration/security audit.

A parsed YAML file is not a validated cloud account deployment. A passed Linux build
is not a claim that the other operating systems have been tested. No live production
address has been guessed. See DEPLOYMENT.md and SECURITY.md for release gates.

## Deliverables
- `Staffdesk-source.zip`: complete project source, Maven wrapper, SQL migrations, tests,
  Docker/Compose/Render/CI configuration, scripts, full documentation and sanitized evidence.
- `Staffdesk-Linux-x64.tar.gz`: unsigned Linux native app-image with bundled Java runtime;
  requires Linux graphical runtime libraries. Set STAFFDESK_API_URL before launch.
- `Staffdesk-api-1.0.0.jar`: executable backend JAR; requires JDK/JRE 21, database and secrets.
- `SHA256SUMS.txt`: checksums of release downloads.

Source lives in the `staffdesk/` workspace folder as well. Build products/dependency
caches, passwords, real credentials, encryption identities and database backups are
not included in the source archive. The provided Linux package and backend contain
no configured server secret or default account. See README.md to run them.
