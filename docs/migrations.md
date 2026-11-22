# Database migrations

Forward schema changes are versioned Flyway migrations in
`backend/src/main/resources/db/migration`. The application applies pending
migrations at startup. V1–V5 create the schema, seed synthetic data, add durable
incident events and visibility, and index audit-history lookups.

Flyway Community runs forward migrations only. V5 is a data-preserving index
migration with a paired manual rollback in
`backend/src/main/resources/db/rollback/R5__audit_log_resource_history_index.sql`.
The rollback removes the index and its V5 history row; it does not modify audit
records. The Spring test `MigrationReversibilityTest` applies all migrations to
a temporary PostgreSQL schema, runs the rollback, verifies stored incidents
remain intact, migrates with target 4, then reapplies V5.

For a manual rollback, stop the application first. From the repository root in
PowerShell, run:

```powershell
Get-Content -Raw backend/src/main/resources/db/rollback/R5__audit_log_resource_history_index.sql |
  docker compose -f docker/docker-compose.yml exec -T postgres psql -U postgres -d incident_desk -v ON_ERROR_STOP=1 -f -
```

Set `FLYWAY_TARGET=4` for the application while V5 is rolled back; otherwise
the default `latest` target will apply V5 again at startup. Set the target back
to `latest` when ready to reapply the index. Do not roll back V4 after private
reports have been created: dropping its visibility columns would discard the
privacy state.

Run the migration test with the usual backend verification command:

```powershell
mvn -f backend/pom.xml verify
```

The test uses a uniquely named temporary schema in the configured PostgreSQL
database and drops that schema after completion. Configure the database through
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`; local defaults match `docker-compose.yml`.
