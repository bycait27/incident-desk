package com.incidentdesk.controllers;

import java.net.URI;
import java.util.List;

import com.incidentdesk.dto.IncidentCreateRequest;
import com.incidentdesk.dto.IncidentUpdateRequest;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.IncidentNotFoundException;
import com.incidentdesk.services.IncidentService;
import com.incidentdesk.services.IncidentService.IncidentSnapshot;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<List<Incident>> getAllIncidents(@AuthenticationPrincipal OidcUser user) {
        IncidentSnapshot snapshot = incidentService.getIncidentSnapshot(isOperator(user));
        return ResponseEntity.ok()
                .header("X-Incident-Event-Cursor", Long.toString(snapshot.eventCursor()))
                .body(snapshot.incidents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncidentById(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        return incidentService.getIncidentById(id, isOperator(user))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<Incident> createIncident(
            @Valid @RequestBody IncidentCreateRequest request,
            @AuthenticationPrincipal OidcUser user) {
        Incident created = incidentService.createIncident(
                request.title(), request.description(), user.getEmail());
        return ResponseEntity.created(URI.create("/api/incidents/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Incident> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentUpdateRequest request,
            @AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request, user.getEmail()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Incident> publishIncident(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok(incidentService.publishIncident(id, user.getEmail()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveIncident(
            @PathVariable Long id,
            @AuthenticationPrincipal OidcUser user) {
        incidentService.archiveIncident(id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private boolean isOperator(OidcUser user) {
        return user != null && user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OPERATOR"::equals);
    }
}
