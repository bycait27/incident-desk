import {
  jwtVerify,
  createRemoteJWKSet,
  errors,
  type JWTPayload,
} from "jose";
import type { AuthInfo } from "@modelcontextprotocol/server";
import type { Request, Response, NextFunction } from "express";
import type { McpConfig } from "./config.js";

export interface TokenIdentity {
  authInfo: AuthInfo;
  subject: string;
  email?: string;
  roles: string[];
}

export type AuthenticatedRequest = Request & {
  auth: AuthInfo;
  identity: TokenIdentity;
};

function stringArray(value: unknown): string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string")
    ? value
    : [];
}

function tokenScopes(payload: JWTPayload): string[] {
  if (typeof payload.scope === "string") {
    return payload.scope.split(/\s+/).filter(Boolean);
  }
  return stringArray(payload.scope);
}

export function createTokenVerifier(config: McpConfig) {
  const jwks = createRemoteJWKSet(new URL(`${config.issuer}/oauth2/jwks`));

  return async (token: string): Promise<TokenIdentity> => {
    const { payload } = await jwtVerify(token, jwks, {
      algorithms: ["RS256"],
      issuer: config.issuer,
      audience: config.resourceUrl,
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) {
      throw new Error("The access token is missing its subject.");
    }

    const authInfo: AuthInfo = {
      token,
      clientId: typeof payload.client_id === "string" ? payload.client_id : "incident-desk-mcp",
      scopes: tokenScopes(payload),
      expiresAt: payload.exp,
      resource: new URL(config.resourceUrl),
      resourceMetadataUrl: resourceMetadataUrl(config),
      extra: {
        subject: payload.sub,
        email: typeof payload.email === "string" ? payload.email : "",
        roles: stringArray(payload.roles),
      },
    };

    return {
      authInfo,
      subject: payload.sub,
      email: typeof payload.email === "string" ? payload.email : undefined,
      roles: stringArray(payload.roles),
    };
  };
}

export function resourceMetadataUrl(config: McpConfig): string {
  const resource = new URL(config.resourceUrl);
  return `${resource.origin}/.well-known/oauth-protected-resource${resource.pathname}`;
}

export function createBearerAuthMiddleware(config: McpConfig) {
  const verify = createTokenVerifier(config);
  const metadataUrl = resourceMetadataUrl(config);

  return async (request: Request, response: Response, next: NextFunction) => {
    const authorization = request.header("authorization");
    const token = authorization?.match(/^Bearer\s+(.+)$/i)?.[1];
    if (!token) {
      sendUnauthorized(response, metadataUrl);
      return;
    }

    try {
      const identity = await verify(token);
      const authenticatedRequest = request as AuthenticatedRequest;
      authenticatedRequest.auth = identity.authInfo;
      authenticatedRequest.identity = identity;
      next();
    } catch (error) {
      if (error instanceof errors.JOSEError && !(error instanceof errors.JWKSTimeout)) {
        sendUnauthorized(response, metadataUrl);
        return;
      }
      response.status(503).json({
        error: "authorization_service_unavailable",
        error_description: "The access-token signing keys are temporarily unavailable.",
      });
    }
  };
}

function sendUnauthorized(response: Response, metadataUrl: string): void {
  response
    .status(401)
    .setHeader("WWW-Authenticate", `Bearer resource_metadata="${metadataUrl}"`)
    .json({ error: "unauthorized", error_description: "A valid MCP access token is required." });
}
