import { z } from "zod";

const configSchema = z.object({
  port: z.coerce.number().int().positive().default(3001),
  resourceUrl: z.string().url(),
  issuer: z.string().url().transform((value) => value.replace(/\/+$/, "")),
  backendApiBaseUrl: z.string().url().transform((value) => value.replace(/\/+$/, "")),
  allowedOrigins: z.array(z.string().url().refine((value) => {
    const origin = new URL(value);
    return origin.origin === value && (origin.protocol === "https:" || origin.hostname === "localhost" || origin.hostname === "127.0.0.1");
  })).min(1),
});

export type McpConfig = z.infer<typeof configSchema>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): McpConfig {
  const parsed = configSchema.safeParse({
    port: env.PORT ?? "3001",
    resourceUrl: env.MCP_RESOURCE_URL ?? "http://localhost:3001/mcp",
    issuer: env.MCP_OAUTH_ISSUER ?? "http://localhost:8080",
    backendApiBaseUrl: env.BACKEND_API_BASE_URL ?? "http://localhost:8080/api/mcp",
    allowedOrigins: (env.MCP_ALLOWED_ORIGINS ?? "http://localhost:5173")
      .split(",")
      .map((origin) => origin.trim())
      .filter(Boolean),
  });
  if (!parsed.success) {
    throw new Error(`Invalid MCP server configuration: ${parsed.error.message}`);
  }
  return parsed.data;
}
