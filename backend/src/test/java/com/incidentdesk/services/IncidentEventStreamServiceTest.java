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
        IncidentEvent firstEvent = event(5L, true);
        IncidentEvent secondEvent = event(6L, true);
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(4L))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(6L))
                .thenReturn(List.of());

        streamService.subscribe(4L, false);
        streamService.publishPendingEvents();
        streamService.publishPendingEvents();

        InOrder calls = inOrder(eventRepository);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(4L);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(6L);
    }

    @Test
    void publicSubscriptionSkipsPrivateEventsButAdvancesItsReplayCursor() {
        streamService = new IncidentEventStreamService(eventRepository);
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(4L))
                .thenReturn(List.of(event(5L, false)));
        when(eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(5L))
                .thenReturn(List.of());

        streamService.subscribe(4L, false);
        streamService.publishPendingEvents();
        streamService.publishPendingEvents();

        InOrder calls = inOrder(eventRepository);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(4L);
        calls.verify(eventRepository).findTop100ByIdGreaterThanOrderByIdAsc(5L);
    }

    private IncidentEvent event(Long id, boolean published) {
        IncidentEvent event = new IncidentEvent(8L, "CREATED", "{\"eventType\":\"CREATED\"}", published, false);
        event.setId(id);
        return event;
    }
}
