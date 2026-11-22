package com.incidentdesk.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.incidentdesk.dto.IncidentUpdateRequest;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.entities.IncidentEvent;
import com.incidentdesk.entities.IncidentEventStreamLock;
import com.incidentdesk.repositories.IncidentEventRepository;
import com.incidentdesk.repositories.IncidentEventStreamLockRepository;
import com.incidentdesk.repositories.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEventRepository incidentEventRepository;

    @Mock
    private IncidentEventStreamLockRepository eventStreamLockRepository;

    @Mock
    private AuditLogService auditLogService;

    private IncidentService incidentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        incidentService = new IncidentService(
                incidentRepository, incidentEventRepository, eventStreamLockRepository,
                auditLogService, objectMapper);
    }

    private void stubEventStreamLock() {
        when(eventStreamLockRepository.lockForEventWrite())
                .thenReturn(Optional.of(mock(IncidentEventStreamLock.class)));
    }

    @Test
    void publicSnapshotsExcludeUnpublishedAndArchivedIncidents() {
        when(incidentRepository.findByPublishedTrueAndArchivedFalseOrderByCreatedAtDesc())
                .thenReturn(List.of(new Incident()));
        when(incidentEventRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

        IncidentService.IncidentSnapshot snapshot = incidentService.getIncidentSnapshot(false);

        assertEquals(1, snapshot.incidents().size());
        assertEquals(0L, snapshot.eventCursor());
        verify(incidentRepository).findByPublishedTrueAndArchivedFalseOrderByCreatedAtDesc();
    }

    @Test
    void persistsCreatedIncidentEventWithFullSnapshot() throws Exception {
        stubEventStreamLock();
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(8L);
            incident.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
            incident.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
            return incident;
        });

        incidentService.createIncident("Synthetic outage", "A test incident.", "operator@example.test");

        ArgumentCaptor<IncidentEvent> eventCaptor = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(eventStreamLockRepository).lockForEventWrite();
        verify(incidentEventRepository).save(eventCaptor.capture());
        IncidentEvent event = eventCaptor.getValue();
        assertEquals(8L, event.getIncidentId());
        assertEquals("CREATED", event.getEventType());
        assertEquals(false, event.isPublished());
        var payload = objectMapper.readTree(event.getPayloadJson());
        assertEquals("CREATED", payload.get("eventType").asText());
        assertEquals("Synthetic outage", payload.get("incident").get("title").asText());
        assertEquals(false, payload.get("incident").get("published").asBoolean());
        verify(auditLogService).logAction(
                eq("operator@example.test"), eq("CREATE"), eq("INCIDENT"), eq(8L), isNull(),
                contains("\"title\":\"Synthetic outage\""));
    }

    @Test
    void publishesPrivateIncidentAndPersistsPublicEvent() {
        stubEventStreamLock();
        Incident pending = new Incident();
        pending.setId(8L);
        pending.setTitle("Synthetic outage");
        pending.setDescription("A test incident.");
        pending.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        pending.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        pending.setPublished(false);
        when(incidentRepository.findByIdAndArchivedFalse(8L)).thenReturn(Optional.of(pending));
        when(incidentRepository.saveAndFlush(pending)).thenReturn(pending);

        incidentService.publishIncident(8L, "operator@example.test");

        ArgumentCaptor<IncidentEvent> eventCaptor = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(incidentEventRepository).save(eventCaptor.capture());
        IncidentEvent event = eventCaptor.getValue();
        assertEquals("PUBLISHED", event.getEventType());
        assertEquals(true, event.isPublished());
        verify(auditLogService).logAction(
                eq("operator@example.test"), eq("PUBLISH"), eq("INCIDENT"), eq(8L),
                contains("\"published\":false"), contains("\"published\":true"));
    }

    @Test
    void persistsUpdatedIncidentEventAfterApplyingChanges() throws Exception {
        stubEventStreamLock();
        Incident existing = new Incident();
        existing.setId(8L);
        existing.setTitle("Synthetic outage");
        existing.setDescription("Original description.");
        existing.setStatus("OPEN");
        existing.setSeverity("LOW");
        existing.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        existing.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        IncidentUpdateRequest update = new IncidentUpdateRequest(
                "Synthetic outage", "Resolved description.", "RESOLVED", "LOW", null);
        when(incidentRepository.findByIdAndArchivedFalse(8L)).thenReturn(Optional.of(existing));
        when(incidentRepository.saveAndFlush(existing)).thenReturn(existing);

        incidentService.updateIncident(8L, update, "operator@example.test");

        ArgumentCaptor<IncidentEvent> eventCaptor = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(eventStreamLockRepository).lockForEventWrite();
        verify(incidentEventRepository).save(eventCaptor.capture());
        IncidentEvent event = eventCaptor.getValue();
        assertEquals("UPDATED", event.getEventType());
        var payload = objectMapper.readTree(event.getPayloadJson());
        assertEquals("RESOLVED", payload.get("incident").get("status").asText());
        assertEquals("Resolved description.", payload.get("incident").get("description").asText());
        assertEquals(true, event.isPublished());
    }

    @Test
    void archivesIncidentWithoutDeletingItsHistory() {
        stubEventStreamLock();
        Incident existing = new Incident();
        existing.setId(8L);
        existing.setTitle("Synthetic outage");
        existing.setDescription("A test incident.");
        existing.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        existing.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        when(incidentRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(incidentRepository.saveAndFlush(existing)).thenReturn(existing);

        incidentService.archiveIncident(8L, "operator@example.test");

        ArgumentCaptor<IncidentEvent> eventCaptor = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(incidentEventRepository).save(eventCaptor.capture());
        assertEquals("ARCHIVED", eventCaptor.getValue().getEventType());
        assertEquals(true, eventCaptor.getValue().isArchived());
        verify(auditLogService).logAction(
                eq("operator@example.test"), eq("ARCHIVE"), eq("INCIDENT"), eq(8L),
                contains("\"title\":\"Synthetic outage\""), contains("\"archived\":true"));
    }
}
