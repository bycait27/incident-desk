# Game Day: staging MCP outage

## Summary

On November 24, 2026, I approved a planned stop and restart of the staging MCP
service. I wanted to practice finding and recovering from an outage without
touching production or PostgreSQL. The times below are UTC and come from the
checks made during the exercise.

After the Railway deployment was stopped, the staging MCP health endpoint
stopped responding successfully. It returned HTTP 200 after the restart. The
staging app's health endpoint was healthy when checked during the exercise,
and both production health endpoints were healthy in the post-exercise check.

## Impact and detection

The exercise intentionally interrupted staging MCP. Requests could have failed
while the service was stopped or restarting, but actual tool calls and user
impact weren't measured. We detected the outage by polling
`https://staging-mcp.csc436.com/healthz`. The first unsuccessful `curl` check
reported `000`, meaning it didn't receive an HTTP response. The exact
connection failure wasn't captured.

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

The checks were intermittent, so they show when the outage and recovery were
observed, not the exact start or duration of the outage.

## Cause and recovery

The outage was caused by intentionally stopping the staging MCP deployment.
The same deployment was restarted through Railway, and its health endpoint was
checked again. This exercise didn't involve a rollback or a database operation.

## What went well and follow-up

- Checking the app separately helped show that its readiness check was still
  passing while MCP was down.
- MCP recovered without rebuilding or changing the database.
- A healthy endpoint doesn't prove that OAuth or MCP tools work end to end.
  Next time, I should also run an authenticated tool check after recovery.
- Detection in this exercise was manual. The Better Stack alert was tested
  separately, as described below.
- The Railway rollback workflow wasn't tested during this outage. The
  separate rollback exercise is described below.

## Follow-up: staging MCP rollback

I approved a separate staging-only rollback on November 24. At 17:21:52 UTC,
Railway accepted a rollback to the previous MCP deployment
`2d222b00-8af9-40c1-b214-4ddfaebe0517`. The target had status `REMOVED` but
Railway said it was eligible for rollback. Railway created deployment
`ff9f960e-6363-4c84-a5e8-041b719abd5b`; it was the sole active deployment by
17:22:04, and the staging MCP health check returned HTTP 200 at 17:24:06.
Its snapshot, image digest, and commit message matched the target.

At 17:24:06 UTC, a second Railway rollback restored the pre-exercise
deployment `0233fe9d-ae0f-4b76-bfe7-533674163c12`. Railway created deployment
`a47dbec3-b03b-4ad5-9076-20527e6728ae`, which was the sole active successful
deployment by 17:24:19. Its snapshot, image digest, and commit message matched
the pre-exercise deployment; staging MCP subsequently returned HTTP 200.
The two selected deployments had different recorded commit messages but the
same image digest, so this didn't demonstrate a binary change. Production and
the database weren't modified.

This exercise used Railway's API directly, not the GitHub Actions rollback
workflow. It uncovered a workflow bug: the workflow rejected an eligible
`REMOVED` target. The workflow was updated locally to use Railway's eligibility
field and verify the new deployment's snapshot and image before checking
health. At that point, the updated workflow hadn't been exercised on GitHub
Actions.

Later that day, the merged GitHub Actions workflow was tested against staging
MCP. The [rollback run](https://github.com/bycait27/incident-desk/actions/runs/45453581864)
targeted deployment `a47dbec3-b03b-4ad5-9076-20527e6728ae` and succeeded;
Railway created deployment `c145ee80-4a92-4ffd-b4bd-c49f415be9e1`. The
[restoration run](https://github.com/bycait27/incident-desk/actions/runs/45453694612)
targeted pre-exercise deployment `e23dd577-de55-40a1-834e-c0acc23f86fe`
and succeeded, creating deployment `f03a2df3-bbcd-41b7-b8eb-7a12e2fadcfb`.
Both runs passed their deployment identity and health checks. Afterward,
staging MCP and app readiness both returned HTTP 200. This was also a
staging-only exercise; production and the database weren't changed.

## Follow-up: staging alert exercise

I created a Better Stack monitor for the staging MCP `/healthz` endpoint, with
email notifications enabled and a 180-second check interval. On November 24,
after confirming the monitor was up, I approved an intentional staging-only
outage of up to seven minutes:

| Time (UTC) | Observation or action |
| --- | --- |
| 18:02:48 | Railway accepted a stop of staging MCP deployment `f03a2df3-bbcd-41b7-b8eb-7a12e2fadcfb`. |
| 18:04:13 | Better Stack monitor `5292863` reported `down`; a restart was requested and Railway accepted it. |
| 18:04:34 | Staging MCP `/healthz` returned HTTP 200. |

Better Stack recorded incident `1057628813` as starting at 18:04:10 UTC. Its
timeline shows an email sent at that time, and I confirmed receiving it.
I've left out the recipient information. After the 180-second recovery
validation period, the monitor returned to `up` and the incident resolved at
18:07:34 UTC.  Staging app readiness also returned HTTP 200 after the exercise.
This outage used Railway's stop and restart API, not the rollback workflow;
production and the database weren't changed.