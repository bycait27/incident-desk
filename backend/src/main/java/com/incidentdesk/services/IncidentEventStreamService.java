package com.incidentdesk.services;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.incidentdesk.entities.IncidentEvent;
import com.incidentdesk.repositories.IncidentEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class IncidentEventStreamService {

    private static final long RECONNECT_DELAY_MILLIS = 3_000;
    private static final int HEARTBEAT_INTERVAL_POLLS = 15;

    private final IncidentEventRepository eventRepository;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();

    public IncidentEventStreamService(IncidentEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public SseEmitter subscribe(long lastEventId) {
        SseEmitter emitter = new SseEmitter();
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = new Subscription(emitter, lastEventId);
        subscriptions.put(subscriptionId, subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscriptionId));
        emitter.onTimeout(() -> subscriptions.remove(subscriptionId));
        emitter.onError(error -> subscriptions.remove(subscriptionId));

        try {
            emitter.send(SseEmitter.event()
                    .reconnectTime(RECONNECT_DELAY_MILLIS)
                    .comment("connected"));
        } catch (IOException exception) {
            subscriptions.remove(subscriptionId);
            emitter.completeWithError(exception);
        } catch (IllegalStateException exception) {
            subscriptions.remove(subscriptionId);
        }
        return emitter;
    }

    @Scheduled(fixedDelay = 1_000)
    public void publishPendingEvents() {
        subscriptions.forEach(this::publishForSubscription);
    }

    private void publishForSubscription(UUID subscriptionId, Subscription subscription) {
        try {
            var events = eventRepository.findTop100ByIdGreaterThanOrderByIdAsc(subscription.lastEventId);
            if (events.isEmpty()) {
                subscription.pollsWithoutEvents++;
                if (subscription.pollsWithoutEvents >= HEARTBEAT_INTERVAL_POLLS) {
                    subscription.emitter.send(SseEmitter.event().comment("keepalive"));
                    subscription.pollsWithoutEvents = 0;
                }
                return;
            }

            for (IncidentEvent event : events) {
                subscription.emitter.send(SseEmitter.event()
                        .id(Long.toString(event.getId()))
                        .name("incident")
                        .data(event.getPayloadJson()));
                subscription.lastEventId = event.getId();
            }
            subscription.pollsWithoutEvents = 0;
        } catch (IOException exception) {
            subscriptions.remove(subscriptionId, subscription);
            subscription.emitter.completeWithError(exception);
        } catch (IllegalStateException exception) {
            subscriptions.remove(subscriptionId, subscription);
        }
    }

    private static final class Subscription {
        private final SseEmitter emitter;
        private volatile long lastEventId;
        private int pollsWithoutEvents;

        private Subscription(SseEmitter emitter, long lastEventId) {
            this.emitter = emitter;
            this.lastEventId = lastEventId;
        }
    }
}
