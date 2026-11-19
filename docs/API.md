# API reference

Base path `/api/v1`. All protected routes require `Authorization: Bearer <accessToken>`.
Content-Type/Accept: `application/json`. Live OpenAPI: `/v3/api-docs` when
API_DOCS_ENABLED=true. An exported [OpenAPI snapshot](openapi.json) is included.
Interactive Swagger UI at `/swagger-ui/index.html` is enabled only in docs mode.
Production docs are off by default.

| Method | Path | Access | Behavior |
|---|---|---|---|
| POST | /auth/login | Public, throttled | Return JWT, expiry, user ID/email/role |
| GET | /version | Public | Non-sensitive application/revision identity |
| GET | /employees | All signed-in roles | Search/filter/sort/page |
| GET | /employees/departments | All signed-in roles | Sorted distinct departments |
| GET | /employees/{uuid} | All signed-in roles | Read record |
| POST | /employees | Admin/editor | Create, 201 + Location |
| PUT | /employees/{uuid} | Admin/editor | Full replacement, requires version |
| DELETE | /employees/{uuid}?version=N | Admin/editor | Delete, 204 |
| GET | /users?page=0&size=25 | Admin | Paginated accounts, no password hashes |
| POST | /users | Admin | Create enabled account, 201 |
| PUT | /users/{uuid} | Admin | Set role/enabled; revoke tokens |
| PUT | /users/{uuid}/password | Admin | Reset password; revoke tokens; 204 |

Additional paths: `/actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness` public (status only); `/actuator/prometheus` and
`/actuator/info` require administrator authentication.

## Login
```json
{"email":"you@company.example","password":"your-unique-passphrase"}
```
Response fields: `accessToken`, `expiresAt`, `userId`, `email`, `role`. Tokens are
HS256-signed, checked for issuer, audience, expiry, enabled account and token version.
There is no refresh endpoint; request another session using credentials after expiry.
Five attempts per normalized email per 15-minute window; 429 afterward, including
successful attempts. The limit is shared across replicas. Edge IP/global limits are
also required because per-identity throttling cannot stop random-identity attacks.

## Employee payload
```json
{
  "firstName": "Asha",
  "lastName": "Sharma",
  "email": "asha@company.example",
  "department": "Engineering",
  "jobTitle": "Software Engineer",
  "phone": "+91 555 0100",
  "hireDate": "2022-03-01",
  "status": "ACTIVE"
}
```
Create must omit (or send null) `version`. Response adds UUID `id`, numeric `version`,
UTC `createdAt` and `updatedAt`. A PUT must include the returned version; a DELETE
must send that version as a query parameter. A new modification increments it. A
no-op PUT can preserve the version. Concurrent modifications fail safely with 409.
Emails are normalized to lowercase and trimmed; uniqueness is enforced by PostgreSQL.
Unknown JSON properties are rejected, preventing accidental mass assignment.

Query parameters:
- `q`: case-insensitive literal substring in first + last name + email, max 200 characters.
  SQL wildcard characters in user input are escaped, not interpreted.
- `department`: exact department, max 80; empty = all.
- `status`: ACTIVE / ON_LEAVE / INACTIVE; empty/omitted = all.
- `page`: zero-based, 0–100000. `size`: 1–100, default 25.
- `sort`: lastName (default), firstName, email, department, jobTitle, hireDate, status, updatedAt.
- `direction`: asc (default) / desc. UUID ascending is an additional stable tie-breaker.

Page response: `{ "content": [], "page": 0, "size": 25, "totalElements": 0, "totalPages": 0 }`.
Deep offsets and exact counts have a cost; use a future cursor endpoint for multi-million-row
interactive workloads rather than simply raising the page cap.

## Account payloads
Create: `{"email":"editor@company.example","password":"unique-12-plus-passphrase","role":"EDITOR"}`.
Update: `{"role":"VIEWER","enabled":false}`. Password reset: `{"password":"new-unique-passphrase"}`.
Email is deliberately not updatable. Reset/role/enabled changes increment the server’s
token version. Account configuration changes serialize in a PostgreSQL advisory
transaction lock and protect the final active administrator.

## Error contract
RFC 9457-compatible ProblemDetail; common fields: type, title, status, detail,
instance and requestId. Authentication-filter errors contain status/detail/requestId.
Validation includes `fields` when available. `X-Request-ID` is included in responses.
400 invalid request, 401 missing/expired/revoked token, 403 forbidden, 404 missing
record, 409 duplicate email/stale version/account invariant, 413 >16 KiB request,
415 wrong content type, 429 login throttled, 500 sanitized unexpected error.
Never rely on database exception strings. JWTs/passwords are never returned in errors.

### API smoke test
```sh
API_URL=https://YOUR-VERIFIED-API-HOST python3 scripts/smoke-api.py
# Add SMOKE_EMAIL + SMOKE_PASSWORD (an editor/admin) for a temporary CRUD round trip.
```
The script verifies real certificates using the OS trust store, rejects redirects,
cleans up its temporary employee and prints the verified API origin. No public
URL has yet been supplied/provisioned for this delivery.
