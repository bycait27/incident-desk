# F3 — Verify the missing staging incident

On 2026-11-24, I approved registering a temporary SSH key with Railway.
The SSH connection targeted the `incident-desk-postgres`
service in the `staging` environment, as resolved by Railway's SSH
configuration. The following read-only query was run with `psql` against the
`railway` database:

```sql
SELECT count(*) FROM incidents WHERE id = 9223372036854775807;
```

Actual output (`psql -tAc`, exit 0):

```text
0
```

This independently confirms the ID was absent from the staging `incidents`
table at query time, including archived records. The earlier
[anonymous curl response](F3-rest-transcript.txt) returned 404 for this ID.
The temporary Railway SSH key was removed after the query, as were its local
private and public key files. No database connection credentials are included.
