# Incident Desk operations runbook

## Scope and access

This runbook covers the synthetic Incident Desk application, MCP service, and
PostgreSQL database on Railway. An operator needs access to the Railway
`incident-desk` project, its staging and production environments, GitHub
Actions for this repository, and the Better Stack monitor. See
[deployment.md](deployment.md) for configuration and environment variables.
Never put tokens, passwords, cookies, or personal data in incident notes.

| Environment | Application and database readiness | MCP readiness |
| --- | --- | --- |
| Staging | https://staging-status.csc436.com/healthz | https://staging-mcp.csc436.com/healthz |
| Production | https://status.csc436.com/healthz | https://mcp.csc436.com/healthz |

Use `/healthz`. The application returns HTTP 200 and
`{"status":"UP"}` when it can query PostgreSQL; it returns 503 and
`{"status":"DOWN"}` on a database access failure. The MCP endpoint returns
HTTP 200 and `{"status":"ok"}` when its process is healthy. MCP readiness
alone does not verify OAuth, the backend, or individual tool calls.

## Triage

1. Note the UTC time, affected environment, symptoms, and the first failing
   check. Ask whether a deployment or configuration change was made recently.
2. Run the two relevant health checks above from outside Railway. For example,
   from PowerShell:

   ```powershell
   curl.exe -i https://staging-status.csc436.com/healthz
   curl.exe -i https://staging-mcp.csc436.com/healthz
   ```

3. In the Railway project, select the correct environment. Inspect the
   `incident-desk-app`, `incident-desk-mcp`, and `incident-desk-postgres`
   service statuses and recent deployment times. Check application deploy
   logs and build logs for the affected service; avoid copying secrets from
   logs. Alternatively, with Railway CLI authenticated and linked:

   ```powershell
   railway deployment list --project incident-desk --environment staging --service incident-desk-app --limit 10
   railway logs --project incident-desk --environment staging --service incident-desk-app --latest --lines 100
   railway logs --project incident-desk --environment staging --service incident-desk-mcp --latest --lines 100
   ```

   Replace `staging` with `production` only when investigating production.
4. If the app health check is 503, inspect PostgreSQL status and connection
   errors first. Do not delete the database, volume, or migrations. If the
   monitor endpoint `/api/uptime/monitors` reports `available: false` or
   `stale: true` while the app is healthy, check Better Stack and the
   environment's token and monitor IDs in Railway without exposing values.
   The integration can serve stale data or a degraded response during failure.
5. If the app is healthy but the MCP endpoint is not, inspect the MCP
   deployment. If both readiness endpoints pass but a tool fails, investigate
   OAuth, scopes, backend connectivity, and the tool's error; readiness does
   not prove those paths are functioning.

## Recover and verify

- For a stopped or crashed service, inspect its deployment and restart it from
  the Railway dashboard. For a known-bad deployment, use the rollback workflow
  described in [deployment.md](deployment.md), subject to Railway's deployment
  retention and eligibility. Rollback affects one service at a time and may
  restore its deployment variables; assess app/MCP compatibility and database
  migration compatibility before rolling back. Never run a database rollback
  blindly.
- If the Actions rollback workflow refuses a target or cannot confirm recovery,
  inspect the Railway deployment history and use Railway's dashboard only if a
  previous deployment is offered as rollback-eligible. Do not assume a generic
  health response proves the intended version is active; confirm the active
  deployment ID in Railway as well.
- Repeat both relevant health checks. Open the public application, check that
  incidents load, and verify any affected MCP tool with an authorized test
  account. Check that the original symptom is gone and record the time and
  deployed IDs. Escalate unresolved database, authentication, or deployment
  problems to the project owner; don't declare recovery based only on a
  restart command.

## After the incident

Record impact, detection, timeline, cause, recovery, and follow-up actions in
the Game Day postmortem or incident record. Include links to redacted logs and
deployment IDs where appropriate. Record only observed facts and mark unknowns
as unknown.

## Certificate expiry alerts

The GitHub Actions **Certificate expiry monitor** checks both production
public endpoints daily. It opens an issue when a certificate has 14 days or
less remaining, or when the TLS certificate cannot be validated. Check the
latest workflow run for current details; an existing open issue prevents
duplicates and is not automatically closed. Confirm the certificate and
Railway/Cloudflare renewal status, correct the problem, then close the issue.
The workflow itself also fails on a real alert. Configure GitHub repository
watching for issues and Actions failures if email notifications are desired;
issue creation alone does not establish email delivery.

To exercise the expiry alert without changing certificates, run the workflow
from the default branch and select `exercise: true`. This uses a deliberately
high 365-day threshold for the production app certificate and creates an
issue labeled `[Exercise]` in its title if its actual expiry is within that
window. Close the exercise issue afterward. A TLS validation failure during
the exercise creates a normal alert instead. Scheduled runs and normal manual
runs continue to use 14 days for both production hosts. The workflow must be
present on the default branch for its schedule to run. GitHub may delay
scheduled runs or disable them after repository inactivity; check Actions
periodically because this workflow cannot alert if GitHub Actions stops
running it.

## Availability objective

For staging MCP, the proposed availability SLI is successful Better Stack
`/healthz` checks divided by the checks expected at the 180-second interval
over a rolling 30-day window. Missing or unknown checks count as unsuccessful;
investigate monitoring gaps separately. The target SLO is 99.9%. This measures
the health endpoint, not successful authenticated tool calls. The existing
monitor's email alert fired during the
[staging exercise](gameday-postmortem.md#follow-up-staging-alert-exercise).
No 30-day SLI result or SLO dashboard has been captured yet.