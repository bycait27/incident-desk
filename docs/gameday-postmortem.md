# Game Day: staging MCP outage

## Summary

On 2026-11-24, the project owner approved a controlled stop and restart of the
staging MCP service. The goal was to practice detecting and recovering a
service outage without touching production or PostgreSQL. All times below
are UTC and come from the checks performed during the exercise.

The staging MCP health endpoint stopped responding successfully after its
Railway deployment was stopped and returned HTTP 200 after it was restarted.
The staging application health endpoint remained healthy when checked during
the exercise. Production app and MCP health endpoints were healthy in the
post-exercise check.

## Impact and detection

The exercise intentionally interrupted the staging MCP service. Requests to
that service could fail while it was stopped or restarting; actual tool calls
and user impact were not measured. The fault was detected by polling
`https://staging-mcp.csc436.com/healthz`. `curl` reported HTTP `000` on
the first unsuccessful check, meaning it did not receive an HTTP response;
the exact connection failure was not captured.

## Timeline

| Time (UTC) | Observation or action |
| --- | --- |
| 17:17:54 | Staging MCP returned HTTP 200 and `{"status":"ok"}`; staging app returned HTTP 200 and `{"status":"UP"}`. |
| 17:18:07 | Requested a stop of staging MCP deployment `0233fe9d-ae0f-4b76-bfe7-533674163c12`; Railway returned `deploymentStop: true`. |
| 17:18:08 and 17:18:13 | MCP health checks still returned HTTP 200. |
| 17:18:23 | MCP health check returned `000` (no HTTP response). The staging app separately returned HTTP 200 and `{"status":"UP"}`. |
| 17:18:24 | Requested restart of the same deployment; Railway returned `deploymentRestart: true`. |
| 17:18:25 | MCP health returned HTTP 502 during recovery. |
| 17:18:30 | MCP health returned HTTP 200. |
| 17:18:38 | MCP returned HTTP 200 and `{"status":"ok"}`; staging app returned HTTP 200 and `{"status":"UP"}`. Railway listed the same MCP deployment as `SUCCESS`. Production app and MCP health checks also returned HTTP 200. |

Polling was intermittent, so these checks bound observed recovery rather than
establishing the exact outage start or duration.

## Cause and recovery

The direct cause was the intentional stop of the staging MCP deployment.
The same deployment was restarted through Railway and its health endpoint was
rechecked. No deployment rollback or database operation was performed.

## What went well and follow-up

- The separate app health check helped distinguish an MCP outage from a
  simultaneous app/database readiness failure.
- The service recovered without rebuilding or changing the database.
- Readiness does not prove that OAuth or MCP tools work end to end. Run an
  authenticated tool check after recovery in future exercises.
- Detection in the original exercise was manual; see the later alert exercise
  below for the separately configured Better Stack monitor.
- The separate Railway rollback workflow was not exercised during the outage
  exercise; see the subsequent rollback exercise below.

## Follow-up: staging MCP rollback

The owner approved a separate staging-only rollback on 2026-11-24. At
17:21:52 UTC, Railway accepted a rollback to prior MCP deployment
`2d222b00-8af9-40c1-b214-4ddfaebe0517`. The target had status `REMOVED` but
Railway reported it as rollback-eligible. Railway created deployment
`ff9f960e-6363-4c84-a5e8-041b719abd5b`; it was the sole active deployment by
17:22:04, and the staging MCP health check returned HTTP 200 at 17:24:06.
Its snapshot, image digest, and commit message matched the selected target.

At 17:24:06 UTC, a second Railway rollback restored the pre-exercise
deployment `0233fe9d-ae0f-4b76-bfe7-533674163c12`. Railway created deployment
`a47dbec3-b03b-4ad5-9076-20527e6728ae`, which was the sole active successful
deployment by 17:24:19. Its snapshot, image digest, and commit message matched
the pre-exercise deployment; staging MCP subsequently returned HTTP 200.
The two selected deployments had different recorded commit messages but the
same image digest, so this exercise did not demonstrate a binary change.
Production and the database were not modified.

The exercise used Railway's API directly, not the GitHub Actions rollback
workflow. It exposed a workflow bug: an eligible `REMOVED` target was rejected.
The workflow was updated locally to use Railway's eligibility field and
to verify the new deployment's snapshot and image before checking health; at
that point, the updated workflow had not been exercised on GitHub Actions.

On 2026-11-24, the merged GitHub Actions workflow was subsequently exercised
against staging MCP. The [rollback run](https://github.com/bycait27/incident-desk/actions/runs/45453581864)
targeted deployment `a47dbec3-b03b-4ad5-9076-20527e6728ae` and succeeded;
Railway created deployment `c145ee80-4a92-4ffd-b4bd-c49f415be9e1`. The
[restoration run](https://github.com/bycait27/incident-desk/actions/runs/45453694612)
targeted pre-exercise deployment `e23dd577-de55-40a1-834e-c0acc23f86fe`
and succeeded, creating deployment `f03a2df3-bbcd-41b7-b8eb-7a12e2fadcfb`.
Both runs passed their deployment identity and health checks. Afterward,
staging MCP and app readiness both returned HTTP 200. This was again a
staging-only exercise; production and the database were not changed.

## Follow-up: staging alert exercise

The owner created a Better Stack monitor for the staging MCP `/healthz`
endpoint, with email notifications enabled and a 180-second check interval.
On 2026-11-24, after confirming the monitor was up, the owner approved an
intentional staging-only outage of up to seven minutes:

| Time (UTC) | Observation or action |
| --- | --- |
| 18:02:48 | Railway accepted a stop of staging MCP deployment `f03a2df3-bbcd-41b7-b8eb-7a12e2fadcfb`. |
| 18:04:13 | Better Stack monitor `5292863` reported `down`; a restart was requested and Railway accepted it. |
| 18:04:34 | Staging MCP `/healthz` returned HTTP 200. |

Better Stack recorded incident `1057628813` as started at 18:04:10 UTC.
Its incident timeline records an email sent at 18:04:10 UTC. Recipient
information is intentionally omitted; the owner confirmed receiving the email.
After its 180-second recovery validation period, the monitor returned to `up`,
and the incident resolved at 18:07:34 UTC. Staging app readiness also
returned HTTP 200 after the exercise. The outage used Railway's stop and
restart API, not the rollback workflow; production and the database were not
changed.