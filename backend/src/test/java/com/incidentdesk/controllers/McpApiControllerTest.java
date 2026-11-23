package com.incidentdesk.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.IncidentCreateRequest;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.AuditLogService;
import com.incidentdesk.services.BetterStackMonitorService;
import com.incidentdesk.services.IncidentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class McpApiControllerTest {

    @Test
    void submitWritesMcpToolAuditWithValidatedActorAndCreatedResource() {
        IncidentService incidentService = mock(IncidentService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        McpApiController controller = new McpApiController(
                incidentService,
                mock(BetterStackMonitorService.class),
                auditLogService,
                new ObjectMapper());
        Incident created = new Incident();
        created.setId(42L);
        created.setTitle("Synthetic Wi-Fi outage");
        when(incidentService.createIncident("Synthetic Wi-Fi outage", "The lab access point is offline.",
                "viewer@example.test")).thenReturn(created);
        Jwt jwt = Jwt.withTokenValue("validated-token")
                .header("alg", "RS256")
                .subject("synthetic-google-subject")
                .claim("email", "viewer@example.test")
                .build();

        var response = controller.submitIncident(
                new IncidentCreateRequest("Synthetic Wi-Fi outage", "The lab access point is offline."),
                jwt);

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<String> afterState = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logAction(
                eq("viewer@example.test"),
                eq("MCP_TOOL_CALL_SUCCEEDED"),
                eq("MCP_TOOL"),
                eq(42L),
                isNull(),
                afterState.capture());
        assertTrue(afterState.getValue().contains("\"toolName\":\"submit_incident\""));
        assertTrue(afterState.getValue().contains("\"outcome\":\"SUCCEEDED\""));
    }
}
