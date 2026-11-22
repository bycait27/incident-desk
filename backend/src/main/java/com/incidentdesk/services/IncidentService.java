package com.incidentdesk.services;

import com.incidentdesk.entities.Incident;
import com.incidentdesk.repositories.IncidentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AuditLogService auditLogService;

    public IncidentService(IncidentRepository incidentRepository, AuditLogService auditLogService) {
        this.incidentRepository = incidentRepository;
        this.auditLogService = auditLogService;
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    public Optional<Incident> getIncidentById(Long id) {
        return incidentRepository.findById(id);
    }

    public Incident createIncident(Incident incident, String actorEmail) {
        Incident saved = incidentRepository.save(incident);
        auditLogService.logAction(actorEmail, "CREATE", "INCIDENT", saved.getId(), null, "Title: " + saved.getTitle());
        return saved;
    }

    public Incident updateIncident(Long id, Incident updatedDetails, String actorEmail) {
        Incident existing = incidentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Incident not found with id: " + id));

        String beforeState = "Status: " + existing.getStatus() + ", Severity: " + existing.getSeverity();

        existing.setTitle(updatedDetails.getTitle());
        existing.setDescription(updatedDetails.getDescription());
        existing.setStatus(updatedDetails.getStatus());
        existing.setSeverity(updatedDetails.getSeverity());
        existing.setAssignedTo(updatedDetails.getAssignedTo());

        Incident saved = incidentRepository.save(existing);
        String afterState = "Status: " + saved.getStatus() + ", Severity: " + saved.getSeverity();

        auditLogService.logAction(actorEmail, "UPDATE", "INCIDENT", saved.getId(), beforeState, afterState);
        return saved;
    }
}
