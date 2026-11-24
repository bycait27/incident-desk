# incident-desk deployment

## Architecture

The React build is packaged into the Spring Boot application and served from
the same origin as the REST API and Google sign-in. Railway runs that app,
the MCP server, and a managed PostgreSQL database. The Railway configuration
defines separate `staging` and `production` environments; each environment
gets its own database and independently configured secrets.

Cloudflare remains the DNS provider and CDN. The deployment uses subdomains,
not the domain apex or `www`, so the current website can remain unchanged:

| Environment | App and API | MCP |
| --- | --- | --- |
| Production | `status.csc436.com` | `mcp.csc436.com` |
| Staging | `staging-status.csc436.com` | `staging-mcp.csc436.com` |

The existing apex and `www` DNS records must not be changed for this setup.
Before creating a new record, verify that the proposed subdomain is unused.

## Railway setup

The infrastructure is defined in `.railway/railway.ts`. It intentionally
does not contain credentials. It uses `csc436.com` for the public URLs; the
domain is not a secret or a Railway variable.

1. Create a Railway project named `incident-desk` and `staging` and
   `production` environments.
2. From a local clone of this repository, run `railway link` and select the
   existing project and `staging` environment. This links the CLI to the
   project; do not create a service with Railway's "Deploy from GitHub" flow.
   The services are defined by the IaC below and deployed through the
   GitHub Actions staging gate. Install the same Railway CLI version used by
   CI (the IaC engine requires CLI 5.42.1 or newer), then install the project
   dependencies and preview the changes:

   ```powershell
   npm install --global @railway/cli@5.75.0
   railway --version
   npm ci
   $env:_ = Join-Path (npm root -g) '@railway\cli\bin\railway.exe'
   railway config plan
   ```

   On Windows, the `_` assignment lets the IaC SDK run the bundled executable
   for its CLI version check instead of trying to launch npm's `.cmd` shim.

3. Review the plan before running `railway config apply`. It creates a
   PostgreSQL service, the app service, and the MCP service in that
   environment. Switch to `production` and repeat the plan/review/apply
   sequence there. Railway resources may incur charges; review current
   pricing before applying.
4. After the services exist, add their custom domains in the Railway
   dashboard under each service's **Settings → Networking**, targeting port
   `8080`. Custom-domain registration is not supported by Railway configuration:

   | Environment | App service | MCP service |
   | --- | --- | --- |
   | `staging` | `staging-status.csc436.com` | `staging-mcp.csc436.com` |
   | `production` | `status.csc436.com` | `mcp.csc436.com` |

   Copy the DNS targets and any ownership-verification TXT records shown by
   Railway into Cloudflare as described below. Keep CNAME records DNS-only
   until verification completes.
5. Configure the following service variables separately in each environment
   before the first deploy. Use distinct staging and production credentials:

   - App: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
     `GOOGLE_OPERATOR_SUBJECT`, `BETTERSTACK_API_TOKEN`, and
     `BETTERSTACK_PUBLIC_MONITOR_IDS`.
   - App: `MCP_SIGNING_JWK_BASE64`, `MCP_OAUTH_REDIRECT_URIS`, and
     `MCP_OAUTH_CLIENT_ORIGIN`.
   - MCP service: `MCP_ALLOWED_ORIGINS` is configured to the staging or
     production app origin by the IaC. Add any additional MCP host browser
     origin as a comma-separated exact origin before using the MCP App.

   The config file preserves these values rather than storing them in Git.
   Keep Google and Better Stack credentials, signing keys, and operator
   subjects in Railway's environment-specific secret storage. Generate a
   different MCP signing key for staging and production.

6. Add the Google OAuth callback URI for each environment to its Google OAuth
   client:

   ```text
   https://status.csc436.com/login/oauth2/code/google
   https://staging-status.csc436.com/login/oauth2/code/google
   ```

   Use separate Google OAuth clients for staging and production.

## Cloudflare DNS

After adding each custom domain in Railway, inspect its DNS requirements in
the Railway domain settings or with `railway domain status`. Railway may
require both a CNAME and a TXT ownership-verification record. Copy every
record name and value exactly into Cloudflare; do not change the existing
apex or `www` records. Keep CNAME records DNS-only while Railway verifies the
domains and provisions HTTPS. Only enable the Cloudflare proxy after Railway
reports the domain verified and the certificate valid.

Use Cloudflare's automatic TTL for proxied records. This keeps changes
responsive without overriding Cloudflare's proxy TTL behavior. Exact record
targets, CAA configuration, and certificate checks depend on the provisioned
Railway domains and will be recorded after setup.

Set Cloudflare SSL/TLS mode to **Full (strict)**. Do not enable HSTS until both
production and staging HTTPS endpoints work correctly.

## Build and health checks

The root `Dockerfile` builds the frontend and backend into one non-root
Spring Boot image. `mcp/Dockerfile` builds the MCP server and bundled App.
Both services expose `/healthz`; the app endpoint also checks the database,
returning `503` if it is unavailable. Railway's deployment healthcheck is
configured on that path.

The GitHub Actions workflow runs backend and frontend checks, MCP tests and
build, checks the Railway IaC types, and builds both deployment images. The
Railway deploy jobs are disabled until `RAILWAY_DEPLOY_ENABLED=true` is set
as a repository Actions variable. Before enabling them:

1. Create GitHub Actions environments named `staging` and `production`.
2. Add an environment-scoped Railway project token named `RAILWAY_TOKEN` to
   each environment. Use the token for that specific Railway environment.
3. Add `APP_HEALTH_URL` and `MCP_HEALTH_URL` to each environment's variables,
   using the corresponding origins:

   | GitHub environment | `APP_HEALTH_URL` | `MCP_HEALTH_URL` |
   | --- | --- | --- |
   | `staging` | `https://staging-status.csc436.com` | `https://staging-mcp.csc436.com` |
   | `production` | `https://status.csc436.com` | `https://mcp.csc436.com` |
4. Add the Railway project ID as the repository variable
   `RAILWAY_PROJECT_ID`.
5. Require a reviewer for the GitHub `production` environment. A push to
   `main` then deploys to staging and checks both `/healthz` endpoints before
   the same commit can be approved for production. The workflow summary and
   Railway deployment messages record the commit SHA, GitHub build ID, and
   both Railway deployment IDs. Runs are serialized per Git ref so another
   main-branch deployment cannot replace staging while production approval
   for the tested commit is pending.
6. Only after the Railway environments, Cloudflare domains, credentials, and
   GitHub environment protections are ready, set
   `RAILWAY_DEPLOY_ENABLED=true`.

The workflow builds and tests the commit before deploying. Railway's own
`/healthz` check gates traffic switching; GitHub also checks both public
health URLs after each deploy. A staging health-check failure prevents
production promotion. If a deployed service fails its health check, use the
rollback workflow to restore its previous healthy deployment before retrying.

To roll back a single service, run **Actions → Railway rollback → Run
workflow** from `main`. Choose the environment and service, then enter the ID
of a prior deployment that Railway reports as rollback-eligible. Railway may
show a previous deployment's status as `REMOVED` even when it is eligible.
Find candidate IDs in that service's Railway **Deployments** view or by
running:

```sh
railway deployment list --limit 20 --service incident-desk-app --environment production --project "$RAILWAY_PROJECT_ID"
```

The workflow uses the token and approval rules for that environment, verifies
the target's project, environment, service, and eligibility before rolling it
back, and waits for a new active deployment with the target's snapshot and
image digest before checking `/healthz`. It fails if the snapshot or image
digest is unavailable or if it cannot verify the result. Railway creates a
new deployment ID for a rollback. App and MCP are rolled back independently;
if a change requires both services to be reverted, run the workflow for each
service and verify both health checks.

## Runtime security controls

The backend sends a Content Security Policy, one-year HSTS on secure requests,
`X-Content-Type-Options: nosniff`, and
`Referrer-Policy: strict-origin-when-cross-origin`. CORS allows only the exact
frontend and configured MCP OAuth client origins; it does not use a wildcard.
The OAuth success handler always returns to the configured frontend URL rather
than a previously saved API request.

Fixed-window limits are 10 OAuth authorization starts and 10 callbacks, 30
incident write or MCP audit requests, and 10 monitor reads per minute per
client IP. Limited requests return the standard JSON error with code
`RATE_LIMITED` and a `Retry-After` header. The counters are in memory per
backend instance; use a shared limiter before scaling the backend to multiple
instances. The IP key uses the forwarded remote address, so production must
accept forwarded headers only from the trusted Railway proxy and ensure
client-supplied forwarding headers are overwritten.

## Current setup status

Staging and production PostgreSQL, app, and MCP services are deployed. Both
environments passed the application and MCP health checks through the
deployment workflow, and production deployment requires approval. The
production Better Stack monitor is up. DNS, certificate, CDN, rollback, and
other operational proof still need to be captured in the evidence dossier.
