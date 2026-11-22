package com.incidentdesk.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

class BetterStackMonitorServiceTest {

    private static final String MONITORS_URL =
            "https://incidents.betterstack.com/api/v2/monitors";
    private static final String MONITOR_RESPONSE = """
            {
              "data": [
                {
                  "id": "41",
                  "type": "monitor",
                  "attributes": {
                    "url": "https://status.example.test",
                    "pronounceable_name": "Campus status",
                    "status": "up",
                    "last_checked_at": "2025-01-02T03:04:05Z"
                  }
                },
                {
                  "id": "42",
                  "type": "monitor",
                  "attributes": {
                    "url": "http://internal.example.test",
                    "pronounceable_name": "Private internal monitor",
                    "status": "up",
                    "last_checked_at": null
                  }
                }
              ],
              "pagination": {"next": null}
            }
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MutableClock clock;
    private BetterStackMonitorService monitorService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        clock = new MutableClock(Instant.parse("2025-01-02T03:05:00Z"));
        monitorService = new BetterStackMonitorService(
                restTemplate,
                new ObjectMapper(),
                "synthetic-betterstack-token",
                MONITORS_URL,
                "41",
                clock);
    }

    @Test
    void mapsValidatedMonitorDataAndSendsBearerToken() {
        expectMonitorRequest().andRespond(withSuccess(MONITOR_RESPONSE, MediaType.APPLICATION_JSON));

        var response = monitorService.getMonitors();

        assertTrue(response.available());
        assertFalse(response.stale());
        assertNull(response.message());
        assertEquals(1, response.monitors().size());
        assertEquals("41", response.monitors().get(0).id());
        assertEquals("Campus status", response.monitors().get(0).name());
        assertEquals("up", response.monitors().get(0).status());
        assertEquals(Instant.parse("2025-01-02T03:04:05Z"),
                response.monitors().get(0).lastCheckedAt());
        server.verify();
    }

    @Test
    void cachesSuccessfulResponseForSixtySeconds() {
        expectMonitorRequest().andRespond(withSuccess(MONITOR_RESPONSE, MediaType.APPLICATION_JSON));

        var firstResponse = monitorService.getMonitors();
        var cachedResponse = monitorService.getMonitors();

        assertEquals(firstResponse.monitors(), cachedResponse.monitors());
        assertFalse(cachedResponse.stale());
        server.verify();
    }

    @Test
    void retriesProviderFailureOnceAndReturnsDegradedResponse() {
        expectMonitorRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectMonitorRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var response = monitorService.getMonitors();

        assertFalse(response.available());
        assertFalse(response.stale());
        assertTrue(response.monitors().isEmpty());
        assertEquals("Monitoring data is temporarily unavailable.", response.message());
        server.verify();
    }

    @Test
    void retriesTimeoutOnceAndReturnsDegradedResponse() throws Exception {
        expectMonitorRequest().andRespond(withException(new SocketTimeoutException("synthetic timeout")));
        expectMonitorRequest().andRespond(withException(new SocketTimeoutException("synthetic timeout")));

        var response = monitorService.getMonitors();

        assertFalse(response.available());
        assertEquals("Monitoring data is temporarily unavailable.", response.message());
        server.verify();
    }

    @Test
    void servesRecentCachedDataWhenProviderIsDown() {
        expectMonitorRequest().andRespond(withSuccess(MONITOR_RESPONSE, MediaType.APPLICATION_JSON));
        expectMonitorRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectMonitorRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var initialResponse = monitorService.getMonitors();
        clock.advance(Duration.ofSeconds(61));
        var response = monitorService.getMonitors();

        assertTrue(initialResponse.available());
        assertTrue(response.available());
        assertTrue(response.stale());
        assertEquals(1, response.monitors().size());
        assertTrue(response.message().contains("Showing cached monitoring data"));
        server.verify();
    }

    @Test
    void rejectsMalformedProviderDataWithoutRetrying() {
        expectMonitorRequest().andRespond(
                withSuccess("{\"data\":[{\"id\":\"41\",\"type\":\"monitor\"}]}",
                        MediaType.APPLICATION_JSON));

        var response = monitorService.getMonitors();

        assertFalse(response.available());
        assertEquals("Monitoring data is temporarily unavailable.", response.message());
        server.verify();
    }

    private ResponseActions expectMonitorRequest() {
        return server.expect(ExpectedCount.once(),
                        requestTo(MONITORS_URL + "?per_page=250"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer synthetic-betterstack-token"));
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
