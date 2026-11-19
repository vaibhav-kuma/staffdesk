# Operations runbook

## Health and monitoring
- `/actuator/health/liveness`: JVM/application liveness, not external DB health.
- `/actuator/health/readiness`: includes database; provider must route traffic only when UP.
- `/actuator/health`: public status without credentials/schema details.
- `/api/v1/version`: public build identity for deploy verification (no secrets).
- Prod profile emits structured JSON logs, including generated request IDs, method,
  path, HTTP status and duration. Query strings/payloads/passwords/JWTs are not logged.
- Use provider CPU/memory/restart/DB/disk metrics and an external HTTPS readiness
  monitor. Alert on sustained failures, 5xx >5%, high p95 latency, saturation and backup age.

Prometheus example + alert rules live in deploy/monitoring. Metrics require an ADMIN
JWT that expires in 15 minutes, intentionally not an unauthenticated endpoint. Do
not expose long-lived administrator credentials to a public monitoring service.
Use provider-native metrics by default. If enabling Prometheus, isolate its host,
rotate the credentials_file via a tightly controlled secret broker and implement a
dedicated metrics identity/private management listener before large-scale use.
Alert rules are examples; Alertmanager destinations and delivery tests remain your
responsibility. They are not provisioned by render.yaml.

## Backups and recovery
Choose an approved policy before handling real data. Example target: managed PITR,
nightly off-site encrypted logical backup, 35-day retention, quarterly restore drills.
Agree RPO/RTO with the business; scripts alone do not establish either guarantee.

Managed Render database must use a plan with verified backup/PITR support. Configure
it in the provider dashboard and confirm actual retention. The blueprint does not
claim to configure unsupported retention/PITR YAML fields automatically.

The included scripts require PostgreSQL 17+ command tools, age, sha256sum (GNU coreutils
on macOS), and optionally AWS CLI. Generate an age key on an offline restore system:
`age-keygen -o backup-identity.txt`; store the private identity offline/secret manager.
Only the **public age recipient** belongs on the backup runner.

Schedule `scripts/backup.sh` on a protected host/cron job with env/secret injection:
```sh
export PGHOST=<approved-db-host> PGPORT=5432 PGDATABASE=staffdesk PGUSER=<backup-user>
# Inject PGPASSWORD or use .pgpass with mode 0600; never paste into shared history.
export PGSSLMODE=verify-full
export AGE_RECIPIENT=<age-public-recipient>
export BACKUP_DIR=/srv/encrypted-backups
export BACKUP_S3_URI=s3://YOUR-PRIVATE-BUCKET/staffdesk
bash scripts/backup.sh
```
The script produces an age-encrypted custom pg_dump and checksum, uploads encrypted
files with server-side S3 encryption if configured, and fails on pipeline errors.
Use a private bucket, least-privilege write-only backup principal, retention/object
lock and versioning. Object lifecycle and scheduling are account-specific and must
be configured. Private Render connections may use PGSSLMODE=require, never plaintext
public transport. Backups include password hashes and personal data: protect them.

Restore to a new/disposable database whose name contains `restore`:
```sh
export PGHOST=<restore-host> PGDATABASE=staffdesk_restore PGUSER=<restore-owner>
export AGE_IDENTITY_FILE=/secure/backup-identity.txt
export ALLOW_RESTORE=true
bash scripts/restore.sh /srv/encrypted-backups/<backup>.dump.age
```
Script verifies the checksum, decrypts in a pipe, restores with errors fatal and
checks schema/row counts. The file guard does not replace operator review. Keep
source and target credentials distinct. Start an isolated backend, run authenticated
smoke checks, compare expected data/audit counts and record elapsed recovery time.
Only change production endpoints after approval. Local backup/restore script behavior
was exercised in this sandbox; off-site storage/PITR/scheduling were not provisioned.

## Secret rotation
- JWT key: generate fresh >=32-byte Base64 secret, replace in all replicas and roll
  atomically where possible. This deliberately logs out all sessions (single-key model).
- Password compromise: disable/reset account; token_version invalidates existing JWTs.
- DB password: rotate via provider, update backend secret, restart and verify readiness.
- Deploy hook: regenerate in provider and replace GitHub secret. Never print the URL.
- Desktop must never contain server signing/DB/backup secrets.

## Data and routine maintenance
login_attempts older than one day are deleted hourly (idempotent across replicas).
Audit retention is deliberately not silently automated; agree a policy with compliance,
archive encrypted records and delete in bounded batches when approved. Monitor index
bloat, autovacuum, query latency and DB free space. pg_trgm supports substring searches;
exact counts/deep offset paging still cost work. Departments are free text; standardize
names operationally or add a normalized department entity in a future migration.

## Incidents
1. Check provider status, readiness, revision and structured logs using the request ID.
2. Do not enable debug payload/token logging. Check DB connections/capacity/certificate.
3. Revoke accounts/rotate key if compromise suspected; preserve audit/log evidence.
4. Use the compatible prior release or reviewed recovery procedure, not destructive
   schema repair commands. Test alert delivery and recovery periodically.
