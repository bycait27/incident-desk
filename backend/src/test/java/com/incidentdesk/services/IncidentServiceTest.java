package com.incidentdesk.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        when(eventStreamLockRepository.lockForEventWrite())
                .thenReturn(Optional.of(mock(IncidentEventStreamLock.class)));
    }

    @Test
    void persistsCreatedIncidentEventWithFullSnapshot() throws Exception {
        Incident incident = new Incident();
        incident.setId(8L);
        incident.setTitle("Synthetic outage");
        incident.setDescription("A test incident.");
        incident.setStatus("OPEN");
        incident.setSeverity("LOW");
        incident.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        incident.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        when(incidentRepository.saveAndFlush(incident)).thenReturn(incident);

        incidentService.createIncident(incident, "operator@example.test");

        ArgumentCaptor<IncidentEvent> eventCaptor = ArgumentCaptor.forClass(IncidentEvent.class);
        verify(eventStreamLockRepository).lockForEventWrite();
        verify(incidentEventRepository).save(eventCaptor.capture());
        IncidentEvent event = eventCaptor.getValue();
        assertEquals(8L, event.getIncidentId());
        assertEquals("CREATED", event.getEventType());
        var payload = objectMapper.readTree(event.getPayloadJson());
        assertEquals("CREATED", payload.get("eventType").asText());
        assertEquals("Synthetic outage", payload.get("incident").get("title").asText());
        verify(auditLogService).logAction(
                "operator@example.test", "CREATE", "INCIDENT", 8L, null, "Title: Synthetic outage");
    }

    @Test
    void persistsUpdatedIncidentEventAfterApplyingChanges() throws Exception {
        Incident existing = new Incident();
        existing.setId(8L);
        existing.setTitle("Synthetic outage");
        existing.setDescription("Original description.");
        existing.setStatus("OPEN");
        existing.setSeverity("LOW");
        existing.setCreatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        existing.setUpdatedAt(OffsetDateTime.parse("2026-11-22T19:00:00Z"));
        Incident update = new Incident();
        update.setTitle("Synthetic outage");
        update.setDescription("Resolved description.");
        update.setStatus("RESOLVED");
        update.setSeverity("LOW");
        when(incidentRepository.findById(8L)).thenReturn(Optional.of(existing));
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
    }
}
