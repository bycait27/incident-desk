import {
  defineRailway,
  postgres,
  preserve,
  project,
  service,
} from "railway/iac";

export default defineRailway((context) => {
  const production = context.environmentName === "production";
  const domain = "csc436.com";
  const appDomain = production
    ? `status.${domain}`
    : `staging-status.${domain}`;
  const mcpDomain = production
    ? `mcp.${domain}`
    : `staging-mcp.${domain}`;

  const database = postgres("incident-desk-postgres");
  const app = service("incident-desk-app", {
    healthcheck: "/healthz",
    env: {
      SPRING_DATASOURCE_URL: "jdbc:postgresql://${{incident-desk-postgres.PGHOST}}:${{incident-desk-postgres.PGPORT}}/${{incident-desk-postgres.PGDATABASE}}",
      SPRING_DATASOURCE_USERNAME: database.env.PGUSER,
      SPRING_DATASOURCE_PASSWORD: database.env.PGPASSWORD,
      FRONTEND_URL: `https://${appDomain}`,
      GOOGLE_OAUTH_ENABLED: "true",
      GOOGLE_CLIENT_ID: preserve(),
      GOOGLE_CLIENT_SECRET: preserve(),
      GOOGLE_OPERATOR_SUBJECT: preserve(),
      BETTERSTACK_API_TOKEN: preserve(),
      BETTERSTACK_PUBLIC_MONITOR_IDS: preserve(),
      MCP_OAUTH_ENABLED: "true",
      MCP_OAUTH_ISSUER: `https://${appDomain}`,
      MCP_OAUTH_REDIRECT_URIS: preserve(),
      MCP_SIGNING_JWK_BASE64: preserve(),
      MCP_OAUTH_CLIENT_ORIGIN: preserve(),
      MCP_RESOURCE_URL: `https://${mcpDomain}/mcp`,
    },
    domains: [{ domain: appDomain, port: 8080 }],
  });

  const mcp = service("incident-desk-mcp", {
    healthcheck: "/healthz",
    env: {
      MCP_RESOURCE_URL: `https://${mcpDomain}/mcp`,
      MCP_OAUTH_ISSUER: `https://${appDomain}`,
      BACKEND_API_BASE_URL: "http://${{incident-desk-app.RAILWAY_PRIVATE_DOMAIN}}:8080/api/mcp",
      MCP_ALLOWED_ORIGINS: `https://${appDomain}`,
    },
    domains: [{ domain: mcpDomain, port: 8080 }],
  });

  return project("incident-desk", {
    resources: [database, app, mcp],
  });
});
