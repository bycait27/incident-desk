package com.incidentdesk.controllers;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.IncidentCreateRequest;
import com.incidentdesk.dto.McpToolAuditRequest;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.AuditLogService;
import com.incidentdesk.services.BetterStackMonitorService;
import com.incidentdesk.services.IncidentNotFoundException;
import com.incidentdesk.services.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
public class McpApiController {

    private final IncidentService incidentService;
    private final BetterStackMonitorService monitorService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public McpApiController(
            IncidentService incidentService,
            BetterStackMonitorService monitorService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.monitorService = monitorService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/incidents")
    public List<Incident> listIncidents(@AuthenticationPrincipal Jwt jwt) {
        return incidentService.getIncidentSnapshot(isOperator(jwt)).incidents();
    }

    @GetMapping("/incidents/{id}")
    public Incident getIncident(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return incidentService.getIncidentById(id, isOperator(jwt))
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    @GetMapping("/uptime")
    public BetterStackMonitorService.MonitorStatusResponse getMonitorStatus() {
        return monitorService.getMonitors();
    }

    @PostMapping("/incidents")
    @Transactional
    public ResponseEntity<Incident> submitIncident(
            @Valid @RequestBody IncidentCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String actor = actor(jwt);
        Incident created = incidentService.createIncident(
                request.title(), request.description(), actor);
        recordToolAudit(actor, "submit_incident", "SUCCEEDED", created.getId());
        return ResponseEntity.created(URI.create("/api/mcp/incidents/" + created.getId())).body(created);
    }

    @PostMapping("/tool-audit")
    @Transactional
    public ResponseEntity<Void> auditToolCall(
            @Valid @RequestBody McpToolAuditRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        recordToolAudit(actor(jwt), request.toolName(), request.outcome(), request.resourceId());
        return ResponseEntity.noContent().build();
    }

    private void recordToolAudit(String actor, String toolName, String outcome, Long resourceId) {
        String auditPayload;
        try {
            auditPayload = objectMapper.writeValueAsString(Map.of(
                    "toolName", toolName,
                    "outcome", outcome));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the MCP tool audit record.", exception);
        }
        auditLogService.logAction(
                actor,
                "MCP_TOOL_CALL_" + outcome,
                "MCP_TOOL",
                resourceId,
                null,
                auditPayload);
    }

    private static boolean isOperator(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ROLE_OPERATOR");
    }

    private static String actor(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return StringUtils.hasText(email) ? email : jwt.getSubject();
    }
}
