# AI-Use Log

I used GitHub Copilot in the Copilot app to build Incident Desk, write
documentation, and collect evidence. I also used Copilot in VS Code while
testing the MCP app. The app used Auto model selection, so I haven't named a
specific model.

## F1 — Frontend

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** On November 21, I asked Copilot to “lets get F1
done, create and document the proof and commit and push that.”

**What was accepted:** I kept the strict TypeScript check and frontend CI
work. The [F1 evidence](evidence/F1.md) includes the typecheck, lint, build,
and a test that deliberately introduced a type error.

**What was rejected/independently verified:** Strict mode doesn't ban every
explicit `any`, so a successful typecheck alone wasn't enough. The evidence
also includes a separate source search.

## F2 — Loading, empty, and error states

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help implement and collect
evidence for the frontend's remote-call states.

**What was accepted:** I kept the UI and API tests and the screenshots of
loading, empty, and forced-error states in the [F2 evidence](evidence/F2.md).

**What was rejected/independently verified:** The screenshots and tests cover
the incident list and monitoring panel, not every remote operation. I haven't
treated them as proof that all signed-in action states are covered.

## F3 — REST API

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help implementing and exercising the
API's methods, status codes, validation, and error responses.

**What was accepted:** I kept the server-side validation and the
[REST transcript](evidence/F3-rest-transcript.txt), along with the
[authenticated staging exercise](evidence/F3-browser-exercise.md).

**What was rejected/independently verified:** I compared the observed success
and failure responses with the expected results. The browser exercise shows
selected response details, not a raw `curl -i` transcript for every route.

## F7 — Google login

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** On November 21, I asked, “okay lets do F7 then.”

**What was accepted:** Copilot helped set up Google OIDC and the PKCE checks.

**What was rejected/independently verified:** I kept the
[F7 evidence](evidence/F7.md), including a redacted login capture, rather
than relying on the implementation alone.

## F8 — Token validation

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help implement server-side token
validation and checks for invalid tokens and missing scopes.

**What was accepted:** I kept the Spring Security OIDC validation and the
explicit granted-scope check described in the [F8 evidence](evidence/F8.md).

**What was rejected/independently verified:** Requesting scopes isn't proof
that they were granted, so the app checks them separately. The negative tests
use synthetic tokens; they don't call Google's live JWKS service.

## F9 — Roles and authorization

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help assigning viewer and operator
roles and enforcing their permissions on the server.

**What was accepted:** I chose an operator allowlist based on the validated
OIDC subject and kept the server-side role checks.

**What was rejected/independently verified:** The
[F9 tests](evidence/F9.md) check anonymous 401 and viewer 403 responses with
synthetic identities, not live Google tokens. I also exercised operator
actions in staging.

## F10 — Better Stack integration

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** On November 22, I asked Copilot to “lets do F10.”

**What was accepted:** I kept the Better Stack integration.

**What was rejected/independently verified:** The
[F10 evidence](evidence/F10.md) includes tests with the provider stubbed down
and a separate live smoke test. They check different things, and neither
proves every part of the requirement on its own.

## F5 — Migrations

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** On November 22, I said, “Let's work on F5 now.”

**What was accepted:** Copilot helped create a reversible migration and its
test.

**What was rejected/independently verified:** The test runs in a temporary
schema, not production, and its log doesn't print every rollback statement.
See the [F5 evidence](evidence/F5.md).

## F11 — Live updates

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked, “okay lets do F11,” then tested a staging
disconnect and reconnect.

**What was accepted:** I kept the live-update implementation and the
[reconnect evidence](evidence/F11-live-exercise.txt).

**What was rejected/independently verified:** The browser request showed
`after=18`, but that was the starting cursor, not the replayed event ID. A
separate curl request received event 22; the browser's own replayed ID wasn't
independently confirmed.

## F12–F13 — MCP server and app

**Tool:** GitHub Copilot in the Copilot app and VS Code

**Query/Prompt Purpose:** On November 23, I asked, “okay now for F12 and
F13.” Copilot helped build the MCP server and app, which I later tried against
staging from VS Code.

**What was accepted:** After signing in again, I inspected an actual
`incidentDeskStaging.list_incidents` call and saw the interactive app. The
[host recording](evidence/F13-mcp-app-host.mp4) shows the app.

**What was rejected/independently verified:** Copilot initially gave me an
incident list after MCP authentication failed. The tool output showed it had
used the public REST API instead, so I didn't count that as an MCP call.
Submission later failed because the connection couldn't receive the
confirmation request. I left F12 and F13 partial; the read-only token denial
and live write audit are still unproven.

## F4 and F6 — Storage and audit evidence

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help recording persistence and audit
evidence.

**What was accepted:** After a staging restart and redeploy, the synthetic
report was still present. The audit query showed its creation and update.
The results are in the [F4](evidence/F4-restart.txt) and
[F6](evidence/F6-audit.txt) notes.

**What was rejected/independently verified:** I didn't count those audit rows
as proof that the table was append-only. A read-only privilege check showed
that the staging app's database role could modify or delete them, so F6
remains incomplete.

## D1 — DNS

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help checking the project's public DNS
records, trace, and TTLs.

**What was accepted:** I kept the public trace and hostname TTL checks in the
[DNS evidence](evidence/D1-dns-2026-11-24.md).

**What was rejected/independently verified:** I exported the zone, but the
original is private pending review. The captured TTL rationale covers the
four proxied project hostnames, not every record in the zone.

## D2 — HTTPS and certificate monitoring

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help check HTTPS, CAA, certificate
renewal, and an expiry alert.

**What was accepted:** I kept the certificate monitor and the
[HTTPS evidence](evidence/D2-public-checks-2026-11-24.md), including the
redirect, certificate checks, CAA response, and alert exercise.

**What was rejected/independently verified:** The alert was deliberately
triggered with a 365-day test threshold. I didn't describe it as an actual
14-day expiry alert.

## D3 — Staging and production

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help setting up separate staging and
production environments and moving changes through staging first.

**What was accepted:** I used separate Railway environments with their own
app, MCP service, and database, and configured environment-specific secrets.
The setup is described in the [deployment documentation](deployment.md).

**What was rejected/independently verified:** Copilot checked the environment
health endpoints and the staging-first deployment workflow. The evidence
doesn't by itself prove every secret value is different or show a change
stopped at staging.

## D4 — CI/CD and rollback

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to add deployment identifiers,
health checks, and rollback functionality.

**What was accepted:** I kept the staging-to-production workflow and the
rollback workflow. The [Game Day postmortem](gameday-postmortem.md) records
the staging rollback and restoration.

**What was rejected/independently verified:** A first rollback check exposed
that the workflow rejected an eligible Railway deployment. We corrected it
and exercised the updated workflow. The selected rollback deployments had the
same image digest, so they didn't demonstrate a binary change.

## D5 — Reproducible deployment

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help with Railway configuration,
container builds, and setup instructions.

**What was accepted:** I kept the IaC, Dockerfiles, and
[deployment instructions](deployment.md), along with the setup steps in the
[README](../README.md).

**What was rejected/independently verified:** Staging and production were
deployed, but I haven't captured a clean-clone run proving the instructions
from a fresh machine.

## D6 — CDN caching

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help implement the cache policy
and collect CDN evidence.

**What was accepted:** I kept the fingerprinted-asset cache policy and the
[staging CDN checks](evidence/D6-cdn.md).

**What was rejected/independently verified:** Before the change, staging
assets returned `BYPASS`. Copilot deployed the change to staging, and I
purged one asset. The captured headers then showed MISS, HIT, and 304. I
didn't capture an authenticated staging response or a screenshot of the purge.

## D7 — CORS and security headers

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help configuring exact-origin CORS and
the required security headers.

**What was accepted:** I kept the allowlist and the CSP, HSTS,
`X-Content-Type-Options`, and `Referrer-Policy` configuration.

**What was rejected/independently verified:** The backend tests exercise
allowed and refused preflights and check the headers. Those tests don't
replace a complete public-edge header transcript.

## D8 — Performance

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help measure performance and make
an improvement.

**What was accepted:** I kept the deferred loading of signed-in controls and
the local and staging Lighthouse reports in the
[performance evidence](evidence/D8-performance.md).

**What was rejected/independently verified:** The single-run differences
could be noise, and the compressed initial bundle didn't get smaller. The
reports don't establish a performance improvement or measure real-user INP.

## D9 — Accessibility

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help running accessibility checks and
recording a keyboard walkthrough.

**What was accepted:** I kept the accessible filter names, main landmark,
axe reports, and [keyboard walkthrough](evidence/D9-accessibility.md).

**What was rejected/independently verified:** Axe reported zero violations on
the anonymous staging board. I also recorded the signed-in workflow, but the
checks don't cover every application state.

## D10 — Observability

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to assess the remaining
observability work and help demonstrate an alert.

**What was accepted:** I kept the staging availability-alert exercise
documented in the [Game Day postmortem](gameday-postmortem.md). I confirmed
that I received its email. Copilot also documented a proposed staging MCP
availability SLI and SLO in the [runbook](runbook.md#availability-objective).

**What was rejected/independently verified:** A fired availability alert
doesn't prove OpenTelemetry logs, metrics, or a two-component trace. No
30-day SLI result or SLO dashboard has been captured.

## D11 — Cost

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for a monthly cost estimate at 1×, 10×, and
100× traffic and help identifying cost-control evidence.

**What was accepted:** I kept the assumptions, arithmetic, and pricing
sources in the [cost estimate](evidence/D11-cost-estimate.md), along with a
screenshot showing the project's Cloudflare Free plan.

**What was rejected/independently verified:** The estimate is hypothetical,
not my measured bill or usage. Railway is on a trial and rejected a budget
alert because the workspace has no active subscription.

## S1 — Rate limiting

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to add limits for login, writes, and
the third-party-backed endpoint.

**What was accepted:** I kept the per-IP limits, `429` response, and
`Retry-After` header described in the [deployment documentation](deployment.md).

**What was rejected/independently verified:** The backend tests exercise rate
limiting, but I haven't collected separate live `429` evidence for all three
endpoint classes. The in-memory limiter is per instance.

## S2 — Runbook and Game Day

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help preparing a runbook and practicing
a controlled failure.

**What was accepted:** I kept the [runbook](runbook.md) and
[Game Day postmortem](gameday-postmortem.md), including the staging outage,
recovery, and follow-up exercises.

**What was rejected/independently verified:** I approved the staging-only
outage and checked recovery. Health checks don't prove an authenticated MCP
tool call worked after recovery.

## Q1 — Tests

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help write, run, and document
tests for validation, authorization, the integration, and other project
behavior.

**What was accepted:** I kept the tests and the
[automated-check results](evidence/automated-checks-2026-11-24.md), including
recorded predictions for the focused reruns. Copilot also added an incident
count calculation, its tests, and a single `npm run test:all` command.

**What was rejected/independently verified:** Copilot recorded the incident
count prediction before running the first test and ran all suites with the
single command. I supplied my own prediction for a separate case before
Copilot added and ran it; see [the calculation note](evidence/Q1-incident-count.md).
Passing tests don't prove the entire checklist.

## Q2 — Architecture

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked for help documenting the application
architecture and data relationships.

**What was accepted:** I kept the request-flow and database diagrams in
[architecture.md](architecture.md), including DNS, the CDN, and the
application's trust boundaries.

**What was rejected/independently verified:** The diagram describes the
configured request path; it doesn't prove that each deployed service is
configured exactly as shown.

## Q3 — Evidence standard

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to organize the evidence and
identify missing proof and unsupported claims.

**What was accepted:** I kept the evidence notes, their reproduction steps,
observations, limitations, and the challenged claims recorded in this log.

**What was rejected/independently verified:** Not every claim has a verified
deployment SHA, raw output, a recorded prediction, a deliberate failure, and
a redaction attestation. Some raw evidence remains private, and the checks
performed do not establish that every artifact is free of sensitive data.

## Q4 — Individual ownership

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help document the implementation
and its operational procedures.

**What was accepted:** I kept the architecture, runbook, and evidence notes
as material for reviewing the project.

**What was rejected/independently verified:** The documents and tests do not
demonstrate that I can explain and change every component in a live oral
defense. That still needs to be demonstrated.

## Hard gates

**Tool:** GitHub Copilot

**Query/Prompt Purpose:** I asked Copilot to help build the project and
document evidence while respecting the course's security and data rules.

**What was accepted:** I kept the synthetic test data, server-side
authorization, secret-store configuration, and redacted evidence described
in the project documents.

**What was rejected/independently verified:** The checks performed are not an
exhaustive secret or personal-data audit, and a clean-clone deployment has not
been demonstrated. Documentation alone does not establish that every hard
gate is satisfied.
