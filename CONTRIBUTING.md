# Contributing
Use JDK 21 (recommended) or JDK 22; builds target Java 21. Read docs/ARCHITECTURE.md
before changing boundaries. New endpoints need DTO validation, authorization,
integration tests, OpenAPI documentation and an audit decision. Never serialize
entities or accept arbitrary sort SQL.

Run `./mvnw spotless:apply`, then the full `verify spotless:check` command from
TESTING.md. Do not skip PostgreSQL tests when reviewing database/security changes.
Create new numbered Flyway migrations; do not rewrite applied migrations. Keep
secrets and generated target/artifacts directories untracked. Review dependency
updates and native packages on each supported OS before publishing a release.
