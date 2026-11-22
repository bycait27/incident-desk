package com.incidentdesk.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.entities.IncidentEvent;
import com.incidentdesk.repositories.IncidentEventStreamLockRepository;
import com.incidentdesk.repositories.IncidentEventRepository;
import com.incidentdesk.repositories.IncidentRepository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentEventStreamLockRepository eventStreamLockRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository,
            IncidentEventStreamLockRepository eventStreamLockRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.eventStreamLockRepository = eventStreamLockRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IncidentSnapshot getIncidentSnapshot() {
        List<Incident> incidents = incidentRepository.findAll();
        long eventCursor = incidentEventRepository.findFirstByOrderByIdDesc()
                .map(IncidentEvent::getId)
                .orElse(0L);
        return new IncidentSnapshot(incidents, eventCursor);
    }

    public Optional<Incident> getIncidentById(Long id) {
        return incidentRepository.findById(id);
    }

    @Transactional
    public Incident createIncident(Incident incident, String actorEmail) {
        Incident saved = incidentRepository.saveAndFlush(incident);
        auditLogService.logAction(actorEmail, "CREATE", "INCIDENT", saved.getId(), null, "Title: " + saved.getTitle());
        recordEvent("CREATED", saved);
        return saved;
    }

    @Transactional
    public Incident updateIncident(Long id, Incident updatedDetails, String actorEmail) {
        Incident existing = incidentRepository.findById(id)
            .orElseThrow(() -> new IncidentNotFoundException(id));

        String beforeState = "Status: " + existing.getStatus() + ", Severity: " + existing.getSeverity();

        existing.setTitle(updatedDetails.getTitle());
        existing.setDescription(updatedDetails.getDescription());
        existing.setStatus(updatedDetails.getStatus());
        existing.setSeverity(updatedDetails.getSeverity());
        existing.setAssignedTo(updatedDetails.getAssignedTo());

        Incident saved = incidentRepository.saveAndFlush(existing);
        String afterState = "Status: " + saved.getStatus() + ", Severity: " + saved.getSeverity();

        auditLogService.logAction(actorEmail, "UPDATE", "INCIDENT", saved.getId(), beforeState, afterState);
        recordEvent("UPDATED", saved);
        return saved;
    }

    private void recordEvent(String eventType, Incident incident) {
        try {
            String payload = objectMapper.writeValueAsString(new IncidentEventPayload(eventType, incident));
            eventStreamLockRepository.lockForEventWrite()
                    .orElseThrow(() -> new IllegalStateException("The incident event stream lock is missing."));
            incidentEventRepository.save(new IncidentEvent(incident.getId(), eventType, payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the incident event.", exception);
        }
    }

    public record IncidentSnapshot(List<Incident> incidents, long eventCursor) {
    }

    public record IncidentEventPayload(String eventType, Incident incident) {
    }
}
