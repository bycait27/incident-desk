package com.incidentdesk.services;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;

import com.incidentdesk.entities.IncidentEvent;
import com.incidentdesk.repositories.IncidentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentEventStreamServiceTest {

    @Mock
    private IncidentEventRepository eventRepository;

    private IncidentEventStreamService streamService;

    @Test
    void resumesAfterCursorAndAdvancesWithoutReplayingDeliveredEvents() {
        streamService = new IncidentEventStreamService(eventRepository);
        IncidentEvent firstEvent = event(5L);
        IncidentEvent secondEvent = event(6L);
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(4L))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(6L))
                .thenReturn(List.of());

        streamService.subscribe(4L);
        streamService.publishPendingEvents();
        streamService.publishPendingEvents();

        InOrder calls = inOrder(eventRepository);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(4L);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(6L);
    }

    private IncidentEvent event(Long id) {
        IncidentEvent event = new IncidentEvent(8L, "CREATED", "{\"eventType\":\"CREATED\"}");
        event.setId(id);
        return event;
    }
}
