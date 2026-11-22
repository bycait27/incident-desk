package com.incidentdesk.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.IncidentUpdateRequest;
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
    public IncidentSnapshot getIncidentSnapshot(boolean includeUnpublished) {
        List<Incident> incidents = includeUnpublished
                ? incidentRepository.findByArchivedFalseOrderByCreatedAtDesc()
                : incidentRepository.findByPublishedTrueAndArchivedFalseOrderByCreatedAtDesc();
        long eventCursor = incidentEventRepository.findFirstByOrderByIdDesc()
                .map(IncidentEvent::getId)
                .orElse(0L);
        return new IncidentSnapshot(incidents, eventCursor);
    }

    public Optional<Incident> getIncidentById(Long id, boolean includeUnpublished) {
        return includeUnpublished
                ? incidentRepository.findByIdAndArchivedFalse(id)
                : incidentRepository.findByIdAndPublishedTrueAndArchivedFalse(id);
    }

    @Transactional
    public Incident createIncident(String title, String description, String actorEmail) {
        Incident incident = new Incident();
        incident.setTitle(title.trim());
        incident.setDescription(description.trim());
        incident.setStatus("OPEN");
        incident.setSeverity("LOW");
        incident.setPublished(false);
        incident.setArchived(false);
        Incident saved = incidentRepository.saveAndFlush(incident);
        auditLogService.logAction(actorEmail, "CREATE", "INCIDENT", saved.getId(), null, toJson(saved));
        recordEvent("CREATED", saved);
        return saved;
    }

    @Transactional
    public Incident updateIncident(Long id, IncidentUpdateRequest updatedDetails, String actorEmail) {
        Incident existing = incidentRepository.findByIdAndArchivedFalse(id)
            .orElseThrow(() -> new IncidentNotFoundException(id));

        String beforeState = toJson(existing);
        existing.setTitle(updatedDetails.title().trim());
        existing.setDescription(updatedDetails.description().trim());
        existing.setStatus(updatedDetails.status());
        existing.setSeverity(updatedDetails.severity());
        existing.setAssignedTo(updatedDetails.assignedTo());

        Incident saved = incidentRepository.saveAndFlush(existing);
        auditLogService.logAction(actorEmail, "UPDATE", "INCIDENT", saved.getId(), beforeState, toJson(saved));
        recordEvent("UPDATED", saved);
        return saved;
    }

    @Transactional
    public Incident publishIncident(Long id, String actorEmail) {
        Incident incident = incidentRepository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        if (incident.isPublished()) {
            throw new IncidentConflictException(id);
        }

        String beforeState = toJson(incident);
        incident.setPublished(true);
        Incident published = incidentRepository.saveAndFlush(incident);
        auditLogService.logAction(actorEmail, "PUBLISH", "INCIDENT", id, beforeState, toJson(published));
        recordEvent("PUBLISHED", published);
        return published;
    }

    @Transactional
    public void archiveIncident(Long id, String actorEmail) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        if (incident.isArchived()) {
            throw new IncidentConflictException(id);
        }

        String beforeState = toJson(incident);
        incident.setArchived(true);
        Incident archived = incidentRepository.saveAndFlush(incident);
        auditLogService.logAction(actorEmail, "ARCHIVE", "INCIDENT", id, beforeState, toJson(archived));
        recordEvent("ARCHIVED", archived);
    }

    private void recordEvent(String eventType, Incident incident) {
        try {
            String payload = objectMapper.writeValueAsString(new IncidentEventPayload(eventType, incident));
            eventStreamLockRepository.lockForEventWrite()
                    .orElseThrow(() -> new IllegalStateException("The incident event stream lock is missing."));
            incidentEventRepository.save(new IncidentEvent(
                    incident.getId(), eventType, payload, incident.isPublished(), incident.isArchived()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the incident event.", exception);
        }
    }

    private String toJson(Incident incident) {
        try {
            return objectMapper.writeValueAsString(incident);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the incident audit state.", exception);
        }
    }

    public record IncidentSnapshot(List<Incident> incidents, long eventCursor) {
    }

    public record IncidentEventPayload(String eventType, Incident incident) {
    }
}
