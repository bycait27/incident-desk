package com.incidentdesk.controllers;

import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.IncidentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = "*")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<Incident> getAllIncidents() {
        return incidentService.getAllIncidents();
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
            @RequestHeader(value = "X-User-Email", defaultValue = "analyst@corp.internal") String actorEmail) {
        return incidentService.createIncident(incident, actorEmail);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Incident> updateIncident(
            @PathVariable Long id,
            @RequestBody Incident incidentDetails,
            @RequestHeader(value = "X-User-Email", defaultValue = "analyst@corp.internal") String actorEmail) {
        try {
            Incident updated = incidentService.updateIncident(id, incidentDetails, actorEmail);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
