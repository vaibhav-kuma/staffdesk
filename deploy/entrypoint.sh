#!/bin/sh
set -eu
: "${DB_USER:?DB_USER required}" "${DB_PASSWORD:?DB_PASSWORD required}" "${JWT_SECRET:?JWT_SECRET required}"
if [ -z "${DB_URL:-}" ]; then
 : "${DB_HOST:?DB_HOST or DB_URL required}" "${DB_NAME:?DB_NAME required}"
 # Render internal PostgreSQL has self-signed TLS; require is only for its private network.
 # For a public DB use DB_URL with sslmode=verify-full + a trusted CA instead.
 export DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}?sslmode=${DB_SSLMODE:-require}"
fi
exec java -jar /app/app.jar "$@"
