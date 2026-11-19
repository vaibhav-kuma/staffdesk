#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:?}" "${PGDATABASE:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${AGE_IDENTITY_FILE:?}"
[[ "${ALLOW_RESTORE:-}" == "true" ]] || { echo 'Set ALLOW_RESTORE=true only for an approved disposable/restore target.' >&2; exit 1; }
FILE="${1:?Supply encrypted .dump.age path}"
[[ "$PGDATABASE" == *restore* ]] || { echo 'Safety guard: target database name must contain restore.' >&2; exit 1; }
export PGSSLMODE="${PGSSLMODE:-verify-full}"
(cd "$(dirname "$FILE")" && sha256sum -c "$(basename "$FILE").sha256")
age -d -i "$AGE_IDENTITY_FILE" "$FILE" | pg_restore --dbname="$PGDATABASE" --no-owner --no-acl --clean --if-exists --exit-on-error
psql -v ON_ERROR_STOP=1 -c 'select version,success from flyway_schema_history order by installed_rank;' -c 'select count(*) as employee_count from employees;'
echo 'Restore completed. Start an isolated backend against this DB and run the authenticated smoke test before accepting recovery.'
