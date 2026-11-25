# Automated checks — 2026-11-24

We ran the local checks using application source at commit
`5ffff212ead41ab25e8301488423e88a126bf79d`, with documentation changes
uncommitted. The later merge commit
`ac9b84d7f7f32b5f7ee910630b5e1ae76e06c1fb` has the same committed tree.
We recorded the predictions in the individual F notes before the local reruns.

| Local check | Actual result |
| --- | --- |
| Backend `mvn -B -f backend/pom.xml verify` | Exit 0; 57 tests passed. |
| Focused `MigrationReversibilityTest` | Exit 0; 1 test passed against local PostgreSQL. |
| Focused F8/F9/F10 tests listed below | Exit 0; 37 tests passed: token validation 7, role mapping 3, incident authorization 21, Better Stack integration 6. |
| Frontend strict typecheck, lint, test, build | Each exited 0; 18 frontend tests passed. [Typecheck output](F1-typecheck-2026-11-24.txt). |
| MCP tests and build | Each exited 0; 6 tests passed. |
| Railway configuration typecheck and certificate-monitor tests | Each exited 0; 5 certificate-monitor tests passed. |

Focused commands, run from the repository root:

```powershell
mvn -f backend/pom.xml "-Dtest=MigrationReversibilityTest" test
mvn -f backend/pom.xml "-Dtest=GoogleTokenValidationTest,GoogleRoleAuthoritiesMapperTest,IncidentAuthorizationTest,BetterStackMonitorServiceTest" test
```

The local raw logs are retained privately in the session because some include
local user paths. The typecheck output above is an unmodified local capture;
its exit status was observed separately. No production database was used.

Separately, the earlier successful
[post-merge CI run](https://github.com/bycait27/incident-desk/actions/runs/45459625861)
tested merge commit `ac9b84d7f7f32b5f7ee910630b5e1ae76e06c1fb`.
Its downloaded [backend](CI-backend-2026-11-24.txt),
[frontend](CI-frontend-2026-11-24.txt), and [MCP](CI-mcp-2026-11-24.txt)
job logs are unchanged copies. CI masked some values before download; these
copies were checked for selected secret and email patterns, not exhaustively.
The CI run preceded the local predictions and is independent corroboration,
not the output of the later focused commands.

The migration test verifies index removal, preserved incident and audit
records, and reapplication of V5; its log does not print every rollback SQL
statement. The authorization and provider tests use synthetic identities,
tokens and responses. They do not replace a real-provider login or a complete
manual demonstration of every remote UI operation.

## Later workspace checks

Later on November 24, `npm run test:all` exited 0 with uncommitted application
and documentation changes: 58 backend tests, 24 frontend tests, 6 MCP tests,
and 5 certificate-monitor tests passed. The frontend typecheck, lint, and
build also exited 0. These later results do not describe the committed CI
revision or prove that the workspace was deployed to production.
