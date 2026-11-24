package com.incidentdesk.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

class McpOAuthAuthorizationServerConfigTest {

    private final McpOAuthAuthorizationServerConfig configuration =
            new McpOAuthAuthorizationServerConfig();

    @Test
    void registersPublicPkceClientWithScopedAccessAndExactRedirectUri() {
        RegisteredClientRepository repository = configuration.mcpRegisteredClientRepository(
                "incident-desk-mcp",
                "https://mcp-host.example/oauth/callback");
        RegisteredClient client = repository.findByClientId("incident-desk-mcp");

        assertNotNull(client);
        assertTrue(client.getClientSettings().isRequireProofKey());
        assertEquals(
                java.util.Set.of("incidents:read", "monitoring:read", "incidents:submit", "mcp:audit"),
                client.getScopes());
        assertEquals(
                java.util.Set.of("https://mcp-host.example/oauth/callback"),
                client.getRedirectUris());
    }

    @Test
    void refusesNonLoopbackHttpRedirectUris() {
        assertThrows(IllegalArgumentException.class, () ->
                configuration.mcpRegisteredClientRepository(
                        "incident-desk-mcp",
                        "http://mcp-host.example/oauth/callback"));
    }
}
