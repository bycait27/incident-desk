# Incident Desk

- **Name:** Caitlin Ash
- **ID:** 2231360
- **Commit SHA or Tag:** `final-project`
- **Platform/OS Version:** Windows 11 Pro, 64-bit

Incident Desk is a synthetic campus IT incident board. Anyone can view and
filter published incidents. Signed-in viewers can submit reports for private
review; operators can publish, edit, or archive them. Changes appear live over
Server-Sent Events. The project also provides an OAuth-protected MCP server and
interactive MCP App.

## Live deployment

| Environment | Application | MCP service |
| --- | --- | --- |
| Production | [status.csc436.com](https://status.csc436.com) | [mcp.csc436.com](https://mcp.csc436.com) |
| Staging | [staging-status.csc436.com](https://staging-status.csc436.com) | [staging-mcp.csc436.com](https://staging-mcp.csc436.com) |

Both services expose `/healthz`. The app readiness check includes database
access; MCP readiness alone does not check individual tools or OAuth.
This project revision includes changes that were not in the earlier merged
revision. Some frontend and cache changes were deployed directly to staging;
this revision and production have not been verified to match.

## Known limits

The staging database role can modify or delete audit rows, so the audit trail
is not enforced as append-only. The MCP host recording shows a read, but its
submission confirmation failed; a successful confirmed write and a live
read-token denial remain unproven. The performance measurements do not
establish an improvement or real-user INP. OpenTelemetry logs, metrics, and
a two-component trace have not been demonstrated. The Railway account is on
a temporary trial, with no configured budget alert.

The clean-clone deployment and complete evidence for every remote-call state,
route, accessibility state, and rate-limited endpoint have not been
demonstrated. The evidence notes distinguish local tests, staging checks,
and production observations. The checks performed are not an exhaustive
secret or personal-data audit.

## Local setup

The commands below are for PowerShell from the repository root. CI uses Node.js
22.19, Java 21, Maven, and PostgreSQL 15. Install those tools and Docker with
Compose before starting. The Compose database credentials are for local
development only.

1. Start the local database and install the frontend dependencies:

   ```powershell
   docker compose -f docker/docker-compose.yml up -d
   npm --prefix frontend ci
   ```

2. In one PowerShell window, start the backend. Flyway applies the migrations
   at startup:

   ```powershell
   mvn -f backend/pom.xml spring-boot:run
   ```

3. In a second PowerShell window, start the frontend:

   ```powershell
   npm --prefix frontend run dev
   ```

   Open the URL printed by Vite, normally `http://localhost:5173`. The public
   board works without credentials. Sign-in, writes, Better Stack integration,
   and MCP require additional configuration; missing Better Stack credentials
   produce a degraded monitor state rather than preventing the board from
   loading.

For Google sign-in configuration, see [F7](docs/evidence/F7.md); for MCP
setup, see [MCP server and app](docs/mcp.md). Use synthetic test accounts and
keep credentials in environment-specific secret storage, never in the
repository or frontend bundle. Stop local services when finished; do not
delete the database volume if you need to preserve its data.

## Tests and checks

From the repository root, install the dependencies and start local PostgreSQL
as described above. Then run all automated test suites with one command:

```powershell
npm ci
npm --prefix frontend ci
npm --prefix mcp ci
npm run test:all
```

Additional type, lint, build, and infrastructure checks:

```powershell
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix mcp run build
npm run check:railway
```

The backend migration test uses a temporary PostgreSQL schema. The frontend and
MCP tests use mocks and synthetic data; see individual evidence notes for
predicted results and limitations. CI also builds both container images.

## Documentation and evidence

- [Architecture and database diagram](docs/architecture.md)
- [Incident calculation prediction](docs/evidence/Q1-incident-count.md)
- [Deployment, infrastructure, and rollback](docs/deployment.md)
- [Database migrations and reversible V5](docs/migrations.md)
- [MCP server and interactive app](docs/mcp.md)
- [Operations runbook](docs/runbook.md)
- [Game Day postmortem](docs/gameday-postmortem.md)
- [AI provenance log](docs/ai-provenance.md)
- [Evidence files](docs/evidence/)
- Evidence notes: [F1](docs/evidence/F1.md), [F2](docs/evidence/F2.md),
  [F7](docs/evidence/F7.md), [F8](docs/evidence/F8.md),
  [F9](docs/evidence/F9.md), [F10](docs/evidence/F10.md),
  [F11](docs/evidence/F11.md)
- [CDN checks](docs/evidence/D6-cdn.md),
  [performance](docs/evidence/D8-performance.md),
  [accessibility](docs/evidence/D9-accessibility.md), and
  [cost control and illustrative estimate](docs/evidence/D11-cost-estimate.md)
- [CI/CD workflow](.github/workflows/ci-cd.yml) and
  [Railway configuration](.railway/railway.ts)

Evidence notes describe what was observed and what still needs a manual
demonstration. The [SSE kill-and-recover recording](docs/evidence/F11-kill-recover.mp4)
has been recorded. The
certificate-expiry alert exercise is documented in the
[HTTPS evidence](docs/evidence/D2-public-checks-2026-11-24.md); the
staging availability alert and rollback exercise are recorded in the
postmortem. The cost estimate uses hypothetical scaling assumptions rather
than measured load.
