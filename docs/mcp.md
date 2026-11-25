# MCP server and interactive app

Incident Desk exposes a Streamable HTTP MCP server at `/mcp` and serves a bundled
React/TypeScript MCP App as `ui://incident-desk/dashboard.html`. The app-owned
Spring Authorization Server reuses the existing Google OIDC login; it issues
five-minute, audience-bound access tokens to the statically registered public
MCP client. PKCE is mandatory, only HTTPS or loopback HTTP callback URLs are
accepted, and the MCP resource validates the token signature, issuer, audience,
and expiration when present. Its verifier does not explicitly require an
`exp` claim; the app-issued tokens have one.

## Local setup

1. Start PostgreSQL using the repository's Docker Compose instructions and
   configure the existing Google OAuth client for the backend. Use a dedicated
   synthetic Google account for testing; audit entries record the validated
   actor email.
2. From PowerShell, install the MCP package and create a local signing key in
   the current shell. Keep using this same PowerShell window to start the
   backend in step 3; environment variables do not carry to a different window.
   Keep the output only in the environment; never put it in a file or commit it.

   ```powershell
   npm --prefix mcp install
   Push-Location mcp
   $env:MCP_SIGNING_JWK_BASE64 = node --input-type=module -e "import { generateKeyPair, exportJWK } from 'jose'; const { privateKey } = await generateKeyPair('RS256', { extractable: true }); const jwk = { ...await exportJWK(privateKey), alg: 'RS256', kid: 'incident-desk-local', use: 'sig' }; process.stdout.write(Buffer.from(JSON.stringify(jwk)).toString('base64'));"
   Pop-Location
   ```

3. Configure and run the backend in the same PowerShell window:

   ```powershell
   $env:MCP_OAUTH_ENABLED = 'true'
   $env:MCP_OAUTH_ISSUER = 'http://localhost:8080'
   $env:MCP_RESOURCE_URL = 'http://localhost:3001/mcp'
   $env:MCP_OAUTH_REDIRECT_URIS = 'http://localhost:6274/oauth/callback,http://127.0.0.1:6274/oauth/callback'
   $env:GOOGLE_OAUTH_ENABLED = 'true'
   mvn -f backend/pom.xml spring-boot:run
   ```

   The Google client ID and secret are configured through the existing
   `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` environment variables. Keep the
   signing value and Google secret in a secret store outside the repository.

4. Build and start the MCP service in another window:

   ```powershell
   npm --prefix mcp run build
   npm --prefix mcp start
   ```

5. In an MCP host that supports OAuth authorization code + PKCE, Streamable
   HTTP, MCP Apps, and form elicitation, connect to
   `http://localhost:3001/mcp`. Configure the public client ID
   `incident-desk-mcp` and request these scopes:
   `incidents:read monitoring:read incidents:submit mcp:audit`.
   If the host uses a different OAuth callback, set the exact callback in
   `MCP_OAUTH_REDIRECT_URIS` before restarting the backend.

The app and MCP service accept exact origins only. Set `MCP_ALLOWED_ORIGINS` to
a comma-separated list of exact browser origins when a host needs browser
access; do not use `*`. In production, set the issuer, MCP resource URL, backend
API URL, callbacks, and allowed origins to their HTTPS deployment URLs.
The authorization server's browser origin is separately configured by
`MCP_OAUTH_CLIENT_ORIGIN` (defaults to the local MCP host origin).

## Tools and authorization

| Tool | Required scopes | Behavior |
| --- | --- | --- |
| `list_incidents` | `incidents:read`, `mcp:audit` | Lists incidents visible to the token's role. |
| `get_incident` | `incidents:read`, `mcp:audit` | Reads one visible incident. |
| `check_monitor_status` | `monitoring:read`, `mcp:audit` | Reads Better Stack status through the backend. |
| `submit_incident` | `incidents:submit`, `mcp:audit` | Creates a private report after MCP form elicitation confirms it. |

The backend independently validates the same signed access token and enforces
the corresponding scope on each MCP API route. The MCP App also requires the
user to review the report and select **Confirm and submit** before it calls the
write tool. Tool starts and outcomes are sent to `audit_logs`; the actor is
derived from the validated token. Insufficient-scope denials are audited only
when the token has `mcp:audit`, and the staging database does not enforce
append-only audit records.

## Commands

```powershell
npm --prefix mcp run typecheck
npm --prefix mcp test
npm --prefix mcp run build
mvn -f backend/pom.xml test
```

The MCP service has a non-sensitive readiness endpoint at `/healthz`. OAuth is
disabled by default in the backend; enabling it without a private signing JWK
fails startup rather than silently using an ephemeral key.

## Host exercise

The [VS Code recording](evidence/F13-mcp-app-host.mp4) shows the interactive
app and a detail read. Submission failed because the connection could not
receive the confirmation request. This is partial F13 evidence, not proof of
a successful authorized write. A live read-only token denial and live write
audit have not been demonstrated.
