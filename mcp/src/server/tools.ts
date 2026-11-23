import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import {
  McpServer,
  acceptedContent,
  inputRequired,
  inputResponse,
  requireScopes,
  type ScopeChallengeHandler,
} from "@modelcontextprotocol/server";
import { registerAppResource, registerAppTool, RESOURCE_MIME_TYPE } from "@modelcontextprotocol/ext-apps/server";
import { z } from "zod";
import { BackendApi } from "./backend.js";

const APP_RESOURCE_URI = "ui://campuspulse/dashboard.html";
const confirmationSchema = z.object({ confirm: z.boolean() });

export async function createMcpServer(
  backend: BackendApi,
): Promise<McpServer> {
  const appHtml = await readFile(resolve(process.cwd(), "dist", "mcp-app.html"), "utf8");
  const server = new McpServer({ name: "CampusPulse", version: "1.0.0" });

  registerAppResource(
    server,
    "CampusPulse Incident Desk",
    APP_RESOURCE_URI,
    { description: "Interactive campus service status and incident reporting." },
    async () => ({
      contents: [{
        uri: APP_RESOURCE_URI,
        mimeType: RESOURCE_MIME_TYPE,
        text: appHtml,
      }],
    }),
  );

  const sharedUi = { ui: { resourceUri: APP_RESOURCE_URI } };

  const listIncidentsTool = registerAppTool(server, "list_incidents", {
    title: "List incidents",
    description: "List visible CampusPulse incidents. Requires incidents:read.",
    inputSchema: z.object({}),
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    _meta: sharedUi,
  }, async (_input, context) => {
    const token = context.http?.authInfo?.token;
    if (!token) throw new Error("Authenticated MCP request context is unavailable.");
    return audited(backend, token, "list_incidents", async () => {
      const incidents = await backend.listIncidents(token);
      return result({ incidents });
    });
  });
  listIncidentsTool.update({
    scopeChallenge: createToolScopeChallenge(backend, "list_incidents", ["incidents:read", "mcp:audit"]),
  });

  const getIncidentTool = registerAppTool(server, "get_incident", {
    title: "Get incident details",
    description: "Get one visible CampusPulse incident by ID. Requires incidents:read.",
    inputSchema: z.object({ id: z.number().int().positive() }),
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    _meta: sharedUi,
  }, async ({ id }, context) => {
    const token = context.http?.authInfo?.token;
    if (!token) throw new Error("Authenticated MCP request context is unavailable.");
    return audited(backend, token, "get_incident", async () => {
      const incident = await backend.getIncident(token, id);
      return result({ incident });
    }, id);
  });
  getIncidentTool.update({
    scopeChallenge: createToolScopeChallenge(backend, "get_incident", ["incidents:read", "mcp:audit"]),
  });

  const checkMonitorTool = registerAppTool(server, "check_monitor_status", {
    title: "Check service status",
    description: "Get Better Stack monitor status. Requires monitoring:read.",
    inputSchema: z.object({}),
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
    _meta: sharedUi,
  }, async (_input, context) => {
    const token = context.http?.authInfo?.token;
    if (!token) throw new Error("Authenticated MCP request context is unavailable.");
    return audited(backend, token, "check_monitor_status", async () => {
      const monitorStatus = await backend.getMonitorStatus(token);
      return result({ monitorStatus });
    });
  });
  checkMonitorTool.update({
    scopeChallenge: createToolScopeChallenge(backend, "check_monitor_status", ["monitoring:read", "mcp:audit"]),
  });

  const submitIncidentTool = registerAppTool(server, "submit_incident", {
    title: "Submit an incident report",
    description: "Submit a private incident report after explicit user confirmation. Requires incidents:submit.",
    inputSchema: z.object({
      title: z.string().trim().min(1).max(255),
      description: z.string().trim().min(1).max(4_000),
    }),
    annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: false },
    _meta: sharedUi,
  }, async ({ title, description }, context) => {
    const token = context.http?.authInfo?.token;
    if (!token) throw new Error("Authenticated MCP request context is unavailable.");

    const confirmation = inputResponse(context.mcpReq.inputResponses, "confirm");
    if (confirmation.kind === "elicit" && confirmation.action !== "accept") {
      await auditToolCall(backend, token, "submit_incident", "CANCELLED");
      return {
        isError: true,
        content: [{ type: "text", text: "Incident submission was cancelled." }],
      };
    }

    const accepted = acceptedContent(context.mcpReq.inputResponses, "confirm", confirmationSchema);
    if (accepted?.confirm !== true) {
      await auditToolCall(backend, token, "submit_incident", "AWAITING_CONFIRMATION");
      return inputRequired({
        inputRequests: {
          confirm: inputRequired.elicit({
            message: `Submit this private incident report titled "${title}"?`,
            requestedSchema: confirmationSchema,
          }),
        },
      });
    }

    return audited(backend, token, "submit_incident", async () => {
      return backend.submitIncident(token, { title, description });
    }, undefined, true).then((incident) => result({ incident }));
  });
  submitIncidentTool.update({
    scopeChallenge: createToolScopeChallenge(backend, "submit_incident", ["incidents:submit", "mcp:audit"]),
  });

  return server;
}

function result(payload: Record<string, unknown>) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(payload) }],
    structuredContent: payload,
  };
}

async function audited<T>(
  backend: BackendApi,
  token: string,
  toolName: string,
  operation: () => Promise<T>,
  resourceId?: number,
  successAuditedInOperation = false,
): Promise<T> {
  await auditToolCall(backend, token, toolName, "STARTED", resourceId);
  let value: T;
  try {
    value = await operation();
  } catch (error) {
    await auditToolCall(backend, token, toolName, "FAILED", resourceId);
    throw error;
  }
  if (!successAuditedInOperation) {
    await auditToolCall(
      backend,
      token,
      toolName,
      "SUCCEEDED",
      resourceId,
    );
  }
  return value;
}

export function createToolScopeChallenge(
  backend: Pick<BackendApi, "auditToolCall">,
  toolName: string,
  scopes: readonly [string, ...string[]],
): ScopeChallengeHandler {
  const challenge = requireScopes(...scopes);
  return async (context) => {
    const requiredScopes = await challenge(context);
    if (requiredScopes && context.authInfo?.scopes.includes("mcp:audit")) {
      await auditToolCall(backend, context.authInfo.token, toolName, "DENIED");
    }
    return requiredScopes;
  };
}

async function auditToolCall(
  backend: Pick<BackendApi, "auditToolCall">,
  token: string,
  toolName: string,
  outcome: "STARTED" | "AWAITING_CONFIRMATION" | "SUCCEEDED" | "FAILED" | "DENIED" | "CANCELLED",
  resourceId?: number,
): Promise<void> {
  await backend.auditToolCall(token, { toolName, outcome, resourceId });
}
