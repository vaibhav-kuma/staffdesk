# Developer setup

## Prerequisites and versions
Use JDK 21 LTS (recommended) or JDK 22 with `java`, `javac`, `jar`, `jlink` and
`jpackage` on PATH. Builds target Java 21. Maven Wrapper pins Maven 3.9.11 with
SHA-256 verification. Backend Spring Boot 3.5.16 and JavaFX 21.0.13 are explicitly
pinned. PostgreSQL major 17 and JavaFX/native binaries must match your platform.
Container base tags intentionally receive security patches; record and pin tested
image digests before a controlled production release.

Linux needs GTK 3, a graphical display (or Xvfb for tests), fontconfig and ALSA runtime
libraries. Debian 13/Ubuntu 24.04: `libgtk-3-0t64 libasound2t64 xvfb`. On older Ubuntu,
package names may be `libgtk-3-0 libasound2`. macOS/Windows use native JavaFX libraries
selected by Maven automatically. A normal web browser cannot execute this application.

## Docker-free backend development
1. Create a dedicated local PostgreSQL database and login using a DBA account:
   ```sql
   CREATE ROLE staffdesk LOGIN PASSWORD 'use-a-new-local-secret';
   CREATE DATABASE staffdesk OWNER staffdesk;
   ```
   Development owner needs permission to install trusted `pg_trgm`. Managed services
   may require a DBA to install this extension first.
2. Export secrets in the backend terminal; these are examples of variable names,
   not secret values to reuse:
   ```sh
   export DB_URL=jdbc:postgresql://127.0.0.1:5432/staffdesk
   export DB_USER=staffdesk
   read -rs DB_PASSWORD; export DB_PASSWORD; echo
   export JWT_SECRET="$(openssl rand -base64 48)"
   export BOOTSTRAP_ADMIN_EMAIL=you@your-company.example
   read -rs BOOTSTRAP_ADMIN_PASSWORD; export BOOTSTRAP_ADMIN_PASSWORD; echo
   export API_DOCS_ENABLED=true
   ./mvnw -B clean package -DskipTests
   java -jar backend/target/backend-1.0.0.jar --server.address=127.0.0.1
   ```
   Read prompts above are silent. On PowerShell use `$env:VARIABLE="value"` in a
   private terminal, or a secret manager; shell history must not contain real production secrets.
3. Start the JavaFX desktop from another terminal using the README client settings.

Flyway automatically creates/validates schema on startup. Hibernate uses `validate`,
not `update` or `create`. If migration checksums fail, restore the original migration;
do not edit already-applied SQL or blindly run Flyway repair. Add a new migration.

## Environment reference
| Variable | Required/default | Purpose |
|---|---|---|
| DB_URL | local JDBC URL default | Explicit JDBC URL, not a `postgres://` URL |
| DB_USER | staffdesk | Database login |
| DB_PASSWORD | required | Database password |
| DB_POOL_SIZE | 10 | Maximum connections per replica |
| JWT_SECRET | required | Base64 encoding of >=32 cryptographically random bytes |
| JWT_ISSUER | staffdesk-api | Expected token issuer |
| TOKEN_MINUTES | 15, allowed 1–30 | Access-token lifetime |
| BOOTSTRAP_ADMIN_EMAIL/PASSWORD | empty | One-time explicit initial administrator |
| PORT | 8080 | Backend HTTP port behind TLS ingress |
| SPRING_PROFILES_ACTIVE | unset; prod hosted | Enables structured production logging |
| FORWARD_HEADERS_STRATEGY | none; framework in prod | Trust forwarded headers only behind trusted provider ingress |
| API_DOCS_ENABLED | false | Enable OpenAPI + Swagger routes |
| FLYWAY_ENABLED | true | Set false only after separate DBA-managed migration step |
| APP_REVISION | provider commit/development | Public non-sensitive deployment revision |
| STAFFDESK_API_URL | invalid placeholder | Desktop API HTTPS origin, no path/query/userinfo |
| STAFFDESK_ALLOW_LOCAL_HTTP | false | Explicit HTTP exception for loopback only |

`deploy/entrypoint.sh` can synthesize DB_URL from DB_HOST/DB_PORT/DB_NAME/DB_SSLMODE.
Credentials are separate variables, never embedded in a logged JDBC URL. `.env` is
read by Docker Compose, **not automatically by Java**. Docker Compose values must
be filled before startup. Spring secrets deliberately have no working fallback.

## Maven JavaFX plugin
If using the plugin rather than the runnable classpath jar:
```sh
./mvnw -B -pl desktop -am install -DskipTests
./mvnw -pl desktop javafx:run
```
The initial `install` makes the local `contracts` artifact available to the plugin.
Do not include `-am` on the javafx:run command (the parent is not an application).

## Native packaging
Build on each intended OS/architecture. The packaging scripts put a Java runtime and
native JavaFX jars in an app-image; end users do not need a separate JDK. Keep the
whole directory/app bundle, not just its executable. Additional installer types:
- Linux: `bash scripts/package-desktop.sh deb` (requires dpkg/fakeroot tooling).
- macOS: `bash scripts/package-desktop.sh dmg`; sign and notarize with your Apple identity.
- Windows: `./scripts/package-desktop.ps1 msi`; install a JDK-21-compatible WiX toolset.

Delete the previous `artifacts/Staffdesk` or `artifacts/Staffdesk.app` before rebuilding.
Installer signing and notarization require your organization’s certificates; none are included.
For distribution, provide a launcher setting STAFFDESK_API_URL to your verified HTTPS origin.
Do not hardcode a secret into a native installer. CI publishes portable, unsigned app-images.

## Troubleshooting
- `release version 21 not supported`: correct JAVA_HOME and PATH, then restart terminal.
- Missing GTK/native library: install OS packages; don't reuse a jar set built for another OS.
- `No toolkit found`/DISPLAY: use your local graphical desktop, or Xvfb for automated tests.
- Cannot reach API: verify origin (no `/api/v1` suffix), DNS, certificate chain, firewall and readiness.
- Swagger missing: enable API_DOCS_ENABLED for development and open `/swagger-ui/index.html`.
  Its CSP permits only same-origin resources (plus inline CSS); production docs remain disabled.
- 401: session expired or revoked. Sign in again. 429: wait up to 15 minutes after repeated attempts.
- 409 on editing: refresh and reopen the employee; don't blindly resubmit an old version.
- PostgreSQL connection failures: JDBC prefix, credentials, network/TLS mode and extension privileges.
