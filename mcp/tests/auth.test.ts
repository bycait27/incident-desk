import { createServer, type Server } from "node:http";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { exportJWK, generateKeyPair, SignJWT } from "jose";
import type { McpConfig } from "../src/server/config.js";
import { createTokenVerifier } from "../src/server/auth.js";

describe("MCP access-token validation", () => {
  let server: Server;
  let config: McpConfig;
  let privateKey: Awaited<ReturnType<typeof generateKeyPair>>["privateKey"];
  let publicJwk: Awaited<ReturnType<typeof exportJWK>>;

  beforeAll(async () => {
    const keyPair = await generateKeyPair("RS256");
    privateKey = keyPair.privateKey;
    publicJwk = await exportJWK(keyPair.publicKey);

    server = createServer((_request, response) => {
      response.setHeader("content-type", "application/json");
      response.end(JSON.stringify({
        keys: [{ ...publicJwk, kid: "mcp-test-key", use: "sig", alg: "RS256" }],
      }));
    });
    await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    if (!address || typeof address === "string") throw new Error("The test JWKS server did not start.");
    config = {
      port: 3001,
      resourceUrl: "http://localhost:3001/mcp",
      issuer: `http://127.0.0.1:${address.port}`,
      backendApiBaseUrl: "http://localhost:8080/api/mcp",
      allowedOrigins: ["http://localhost:5173"],
    };
  });

  afterAll(async () => {
    if (server) await new Promise<void>((resolve, reject) => {
      server.close((error) => error ? reject(error) : resolve());
    });
  });

  it("accepts a correctly signed issuer- and audience-bound token", async () => {
    const identity = await createTokenVerifier(config)(await createToken());
    expect(identity.subject).toBe("synthetic-viewer");
    expect(identity.email).toBe("viewer@example.test");
    expect(identity.authInfo.scopes).toEqual(["incidents:read", "mcp:audit"]);
    expect(identity.roles).toEqual(["ROLE_VIEWER"]);
  });

  it.each([
    ["wrong issuer", { issuer: "http://wrong-issuer.example" }],
    ["wrong audience", { audience: "http://another-resource.example/mcp" }],
    ["expired", { expired: true }],
  ])("rejects a token with %s", async (_label, overrides) => {
    await expect(createTokenVerifier(config)(await createToken(overrides))).rejects.toThrow();
  });

  async function createToken(overrides: {
    issuer?: string;
    audience?: string;
    expired?: boolean;
  } = {}): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    return new SignJWT({
      client_id: "campuspulse-mcp",
      scope: "incidents:read mcp:audit",
      email: "viewer@example.test",
      roles: ["ROLE_VIEWER"],
    })
      .setProtectedHeader({ alg: "RS256", kid: "mcp-test-key" })
      .setIssuer(overrides.issuer ?? config.issuer)
      .setAudience(overrides.audience ?? config.resourceUrl)
      .setSubject("synthetic-viewer")
      .setIssuedAt(now)
      .setExpirationTime(overrides.expired ? now - 1 : now + 300)
      .sign(privateKey);
  }
});
