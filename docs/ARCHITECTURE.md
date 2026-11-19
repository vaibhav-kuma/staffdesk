# Staffdesk — architecture decision record

Status: accepted for this implementation, 19 November 2026.

## Scope and selected stack
Employee directory; ADMIN manages accounts and records, EDITOR manages records,
VIEWER reads records. No public registration. Account and employee records are
separate: changing an employee does not create/delete a login.

- Java 21 LTS, Maven multi-module build (wrapper pinned to 3.9.11).
- Spring Boot 3.5.16, Spring Security resource server/Nimbus JWT, JPA, Bean Validation.
- JavaFX 21.0.13 (platform-native dependencies), Java HttpClient, Jackson.
- PostgreSQL 17, Flyway SQL migrations; no H2 substitution in integration tests.
- Springdoc 2.8.17, JUnit 5, Spring Boot integration tests, Testcontainers in CI.
- Docker Compose for local database/backend; Render blueprint for hosted backend + managed database.

## Boundaries
JavaFX -> HTTPS /api/v1 -> stateless Spring Boot replicas -> private PostgreSQL.
The database has no public application-facing endpoint. JavaFX contains neither DB
credentials nor JWT signing secrets. A single backend deployable is a deliberate
modular-monolith choice, not unnecessary microservices. Packages separate auth,
users, employees, configuration and shared error handling. Contract records are
shared with the desktop in a dependency-light `contracts` module.

Project: `contracts/`, `backend/`, `desktop/`, `deploy/`, `scripts/`, `docs/`, `.github/`.

## Data model
- app_users: UUID id; case-normalized unique email; BCrypt password hash; enum role;
  enabled; token_version; created_at. Disabling an account or changing credentials/
  role increments token_version, invalidating all previously issued JWTs.
- employees: UUID id; unique normalized email; first_name, last_name, department,
  job_title, optional phone, hire_date, status (ACTIVE/ON_LEAVE/INACTIVE), optimistic
  lock version, created_at and updated_at. API exposes version for update/delete
  conflict detection. Hard deletion requires confirmation in the desktop.
- audit_events: generated numeric id; actor UUID; action; entity UUID; timestamp.
  No employee names, passwords, tokens or payloads stored in the audit log.
- login_attempts: hashed normalized login identity; fixed-window counters and window
  timestamp persisted in PostgreSQL (shared by all replicas). No IP trust required.

B-tree indexes on department, status and last_name; pg_trgm GIN indexes on lowercased
name/email search expressions. Unique constraints enforce race-safe email uniqueness.
Pagination is bounded to 100, sorting is allowlisted with UUID tie-breaking. Salary,
sensitive payroll, attachments and unrestricted reporting are outside scope.

## Security and concurrency
15-minute HS256 access JWT, minimum 256-bit random key; issuer, audience, signature
and expiry validation. No refresh token or persistent desktop token cache: re-login
when expired. Server validates account enabled + token_version on every request,
so RBAC changes and revocation are immediate across replicas. This trades one DB
read per request for reliable revocation. BCrypt cost 12, bounded passwords to avoid
BCrypt truncation; no default credentials, explicit one-time admin bootstrap.
No cookie authentication; CSRF disabled for stateless bearer API; CORS disabled
(no web frontend). HTTPS mandatory for desktop except explicit loopback dev opt-in.
No TLS trust-all bypass. Public login has persistent per-account throttling, generic
errors and dummy BCrypt verification for unknown accounts. Reverse proxy must also
rate-limit by IP to defend against random-identity abuse. Limit request sizes at edge.
Mutation and audit insertion share a transaction. Stale employee updates/deletes
return 409; validation errors return RFC 9457 ProblemDetail with correlation ID.
ADMIN self-disable/self-demotion is prohibited. Admin operations serialize through
an advisory transaction lock to preserve at least one enabled administrator.

## Desktop
Native desktop, not a browser application. CSS-styled responsive layout, table,
server-side search/filter/sort/page controls, dialogs and role-aware account panel.
Network work uses background futures, callbacks marshal to the FX thread; stale
list responses are discarded. Bearer token remains in memory, logout clears it.
Native packages are built per target OS using jpackage (no cross-OS packaging).
An optional future web SPA could consume these APIs after a separate CORS/OIDC review.

## Operations and acceptance
Flyway validates schema; Hibernate validates, never auto-creates. Environment secrets,
non-root container, readiness/liveness, structured production logging, internal
metrics, encrypted off-site backups and restore drill scripts. Provider terminates
TLS; forwarded headers are interpreted only behind its trusted ingress.
CI runs real PostgreSQL integration tests + JavaFX headless smoke tests, builds
native installers on Windows/Linux/macOS, scans dependencies and container images,
and can invoke a configured Render deploy hook after successful main checks.

Build-first was explicitly selected: no hosting account is available and no public
production URL can be honestly verified. Deployment artifacts and local verification
are deliverables; provider provisioning, live TLS checks, monitoring delivery,
backup scheduling and actual restore drills are operational acceptance gates.
