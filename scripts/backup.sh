#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${PGHOST:?}" "${PGDATABASE:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${AGE_RECIPIENT:?Public age recipient required}"
# Use ~/.pgpass instead of PGPASSWORD when supported by your secret manager.
export PGSSLMODE="${PGSSLMODE:-verify-full}"
DEST="${BACKUP_DIR:-backups}"; mkdir -p "$DEST"
FILE="$DEST/staffdesk-$(date -u +%Y%m%dT%H%M%SZ).dump.age"
trap 'rm -f "$FILE.tmp"' EXIT
pg_dump --format=custom --no-owner --no-acl | age -r "$AGE_RECIPIENT" > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"
(cd "$(dirname "$FILE")" && sha256sum "$(basename "$FILE")") > "$FILE.sha256"
if [[ -n "${BACKUP_S3_URI:-}" ]]; then
 aws s3 cp "$FILE" "$BACKUP_S3_URI/" --sse AES256
 aws s3 cp "$FILE.sha256" "$BACKUP_S3_URI/" --sse AES256
fi
printf 'Encrypted backup created: %s
' "$FILE"
