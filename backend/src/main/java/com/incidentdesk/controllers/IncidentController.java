package com.incidentdesk.controllers;

import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.IncidentNotFoundException;
import com.incidentdesk.services.IncidentService;
import com.incidentdesk.services.IncidentService.IncidentSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = "*", exposedHeaders = "X-Incident-Event-Cursor")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<List<Incident>> getAllIncidents() {
        IncidentSnapshot snapshot = incidentService.getIncidentSnapshot();
        return ResponseEntity.ok()
                .header("X-Incident-Event-Cursor", Long.toString(snapshot.eventCursor()))
                .body(snapshot.incidents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Incident> getIncidentById(@PathVariable Long id) {
        return incidentService.getIncidentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Incident createIncident(
            @RequestBody Incident incident,
            @AuthenticationPrincipal OidcUser user) {
        return incidentService.createIncident(incident, user.getEmail());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Incident> updateIncident(
            @PathVariable Long id,
            @RequestBody Incident incidentDetails,
            @AuthenticationPrincipal OidcUser user) {
        try {
            Incident updated = incidentService.updateIncident(id, incidentDetails, user.getEmail());
            return ResponseEntity.ok(updated);
        } catch (IncidentNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
