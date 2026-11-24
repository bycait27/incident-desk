import type { JSONRPCRequest } from "@modelcontextprotocol/server";
import { describe, expect, it, vi } from "vitest";
import { createToolScopeChallenge } from "../src/server/tools.js";

describe("MCP tool scope authorization", () => {
  it("refuses a read-only token on the incident submission tool and audits the denial", async () => {
    const auditToolCall = vi.fn().mockResolvedValue(undefined);
    const challenge = createToolScopeChallenge(
      { auditToolCall },
      "submit_incident",
      ["incidents:submit", "mcp:audit"],
    );
    const context = {
      request: {
        jsonrpc: "2.0",
        id: 1,
        method: "tools/call",
        params: { name: "submit_incident" },
      } satisfies JSONRPCRequest,
      authInfo: {
        token: "signed-read-only-token",
        clientId: "incident-desk-mcp",
        scopes: ["incidents:read", "monitoring:read", "mcp:audit"],
      },
    };

    const result = await challenge(context);

    expect(result?.scopes).toEqual(["incidents:submit", "mcp:audit"]);
    expect(auditToolCall).toHaveBeenCalledWith(
      "signed-read-only-token",
      { toolName: "submit_incident", outcome: "DENIED", resourceId: undefined },
    );
  });

  it("allows a token with all scopes required by a read tool", async () => {
    const auditToolCall = vi.fn().mockResolvedValue(undefined);
    const challenge = createToolScopeChallenge(
      { auditToolCall },
      "list_incidents",
      ["incidents:read", "mcp:audit"],
    );

    const result = await challenge({
      request: {
        jsonrpc: "2.0",
        id: 2,
        method: "tools/call",
        params: { name: "list_incidents" },
      },
      authInfo: {
        token: "signed-read-token",
        clientId: "incident-desk-mcp",
        scopes: ["incidents:read", "mcp:audit"],
      },
    });

    expect(result).toBeUndefined();
    expect(auditToolCall).not.toHaveBeenCalled();
  });
});
