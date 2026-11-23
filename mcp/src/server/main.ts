import express, { type Request, type Response } from "express";
import { NodeStreamableHTTPServerTransport } from "@modelcontextprotocol/node";
import type { McpServer } from "@modelcontextprotocol/server";
import type { AuthInfo } from "@modelcontextprotocol/server";
import { createBearerAuthMiddleware, resourceMetadataUrl, type AuthenticatedRequest } from "./auth.js";
import { BackendApi } from "./backend.js";
import { loadConfig } from "./config.js";
import { createMcpServer } from "./tools.js";

const config = loadConfig();
const backend = new BackendApi(config);
const app = express();
const resource = new URL(config.resourceUrl);
const metadata = {
  resource: config.resourceUrl,
  authorization_servers: [config.issuer],
  bearer_methods_supported: ["header"],
  scopes_supported: ["incidents:read", "monitoring:read", "incidents:submit", "mcp:audit"],
};

app.disable("x-powered-by");
app.use(express.json({ limit: "256kb" }));
app.use((request, response, next) => {
  const origin = request.header("origin");
  if (origin && !config.allowedOrigins.includes(origin)) {
    response.status(403).json({ error: "origin_not_allowed" });
    return;
  }
  if (origin) {
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Vary", "Origin");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, MCP-Protocol-Version");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  }
  if (request.method === "OPTIONS") {
    response.sendStatus(origin ? 204 : 403);
    return;
  }
  next();
});

app.get("/healthz", (_request, response) => response.json({ status: "ok" }));
app.get("/.well-known/oauth-protected-resource", (_request, response) => response.json(metadata));
app.get(resourceMetadataUrl(config).replace(resource.origin, ""), (_request, response) => response.json(metadata));

const authenticate = createBearerAuthMiddleware(config);
app.all("/mcp", authenticate, (request: Request, response: Response) => {
  void handleMcpRequest(request as AuthenticatedRequest, response);
});

async function handleMcpRequest(request: AuthenticatedRequest, response: Response): Promise<void> {
  let server: McpServer | undefined;
  try {
    const identity = request.identity;
    const mcpServer = await createMcpServer(backend);
    server = mcpServer;
    const transport = new NodeStreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    const transportRequest = request as Request & { auth?: AuthInfo };
    transportRequest.auth = identity.authInfo;
    await mcpServer.connect(transport);
    await transport.handleRequest(transportRequest, response, request.body);
  } catch (error) {
    console.error("MCP request failed", error);
    if (!response.headersSent) {
      response.status(500).json({ error: "mcp_request_failed" });
    }
  } finally {
    if (server?.isConnected()) {
      await server.close();
    }
  }
}

app.listen(config.port, () => {
  console.log(`CampusPulse MCP server listening on port ${config.port}`);
});
