package com.incidentdesk.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class BetterStackMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterStackMonitorService.class);
    private static final String BETTERSTACK_MONITORS_URL =
            "https://incidents.betterstack.com/api/v2/monitors";
    private static final Set<String> MONITOR_STATUSES =
            Set.of("up", "down", "validating", "paused", "pending", "maintenance");
    private static final int MAX_ATTEMPTS = 2;
    private static final int PER_PAGE = 250;
    private static final long RETRY_DELAY_MILLIS = 100;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration MAX_STALE_AGE = Duration.ofMinutes(15);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiToken;
    private final String monitorsUrl;
    private final Set<String> publicMonitorIds;
    private final Clock clock;
    private CacheEntry lastSuccessfulResponse;

    @Autowired
    public BetterStackMonitorService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.betterstack.api-token:}") String apiToken,
            @Value("${app.betterstack.public-monitor-ids:}") String publicMonitorIds) {
        this(restTemplate, objectMapper, apiToken, BETTERSTACK_MONITORS_URL,
                publicMonitorIds, Clock.systemUTC());
    }

    BetterStackMonitorService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            String apiToken,
            String monitorsUrl,
            String publicMonitorIds,
            Clock clock) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiToken = apiToken;
        this.monitorsUrl = monitorsUrl;
        this.publicMonitorIds = parseMonitorIds(publicMonitorIds);
        this.clock = clock;
    }

    public synchronized MonitorStatusResponse getMonitors() {
        Instant now = clock.instant();
        CacheEntry cached = lastSuccessfulResponse;
        if (cached != null && now.isBefore(cached.observedAt().plus(CACHE_TTL))) {
            return response(true, false, cached, null);
        }

        if (publicMonitorIds.isEmpty()) {
            return degradedResponse(cached, now, "No public monitors are configured.");
        }

        if (!StringUtils.hasText(apiToken)) {
            return degradedResponse(cached, now, "External monitoring is not configured.");
        }

        try {
            List<MonitorSummary> monitors = parseMonitors(requestProviderBody());
            CacheEntry successfulResponse = new CacheEntry(now, monitors);
            lastSuccessfulResponse = successfulResponse;
            return response(true, false, successfulResponse, null);
        } catch (ProviderUnavailableException exception) {
            return degradedResponse(cached, now, "Monitoring data is temporarily unavailable.");
        } catch (InvalidProviderResponseException exception) {
            LOGGER.warn("Better Stack returned a response that did not match the expected monitor schema.");
            return degradedResponse(cached, now, "Monitoring data is temporarily unavailable.");
        }
    }

    private String requestProviderBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        var uri = UriComponentsBuilder.fromUriString(monitorsUrl)
                .queryParam("per_page", PER_PAGE)
                .build()
                .encode()
                .toUri();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<String> response =
                        restTemplate.exchange(uri, HttpMethod.GET, request, String.class);
                return response.getBody();
            } catch (HttpServerErrorException | ResourceAccessException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    if (exception instanceof HttpServerErrorException serverError) {
                        LOGGER.warn("Better Stack returned HTTP {} after {} attempts.",
                                serverError.getStatusCode().value(), MAX_ATTEMPTS);
                    } else {
                        LOGGER.warn("Better Stack request timed out or failed after {} attempts.",
                                MAX_ATTEMPTS);
                    }
                    throw new ProviderUnavailableException();
                }
                pauseBeforeRetry();
            } catch (HttpClientErrorException exception) {
                LOGGER.warn("Better Stack rejected the monitor request with HTTP {}.",
                        exception.getStatusCode().value());
                throw new ProviderUnavailableException();
            } catch (RestClientException exception) {
                LOGGER.warn("Better Stack request failed with {}.", exception.getClass().getSimpleName());
                throw new ProviderUnavailableException();
            }
        }
        throw new ProviderUnavailableException();
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException();
        }
    }

    private List<MonitorSummary> parseMonitors(String body) {
        if (!StringUtils.hasText(body)) {
            throw new InvalidProviderResponseException();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new InvalidProviderResponseException();
        }

        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray()) {
            throw new InvalidProviderResponseException();
        }

        List<MonitorSummary> monitors = new ArrayList<>();
        for (JsonNode item : data) {
            if (item == null || !item.isObject() || !"monitor".equals(text(item, "type"))) {
                throw new InvalidProviderResponseException();
            }

            String id = requiredText(item, "id");
            JsonNode attributes = item.get("attributes");
            if (attributes == null || !attributes.isObject()) {
                throw new InvalidProviderResponseException();
            }

            String url = requiredText(attributes, "url");
            String name = text(attributes, "pronounceable_name");
            if (!StringUtils.hasText(name)) {
                name = url;
            }

            String status = requiredText(attributes, "status").toLowerCase(Locale.ROOT);
            if (!MONITOR_STATUSES.contains(status)) {
                throw new InvalidProviderResponseException();
            }

            Instant lastCheckedAt = parseOptionalInstant(attributes, "last_checked_at");
            if (publicMonitorIds.contains(id)) {
                monitors.add(new MonitorSummary(id, name, url, status, lastCheckedAt));
            }
        }
        return List.copyOf(monitors);
    }

    private static Set<String> parseMonitorIds(String configuredIds) {
        if (!StringUtils.hasText(configuredIds)) {
            return Set.of();
        }
        return Arrays.stream(configuredIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Instant parseOptionalInstant(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new InvalidProviderResponseException();
        }
        try {
            return Instant.parse(value.textValue());
        } catch (DateTimeParseException exception) {
            throw new InvalidProviderResponseException();
        }
    }

    private String requiredText(JsonNode object, String field) {
        String value = text(object, field);
        if (!StringUtils.hasText(value)) {
            throw new InvalidProviderResponseException();
        }
        return value;
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private MonitorStatusResponse degradedResponse(CacheEntry cached, Instant now, String message) {
        if (cached != null && now.isBefore(cached.observedAt().plus(MAX_STALE_AGE))) {
            return response(true, true, cached,
                    "Showing cached monitoring data; " + message.toLowerCase(Locale.ROOT));
        }
        return new MonitorStatusResponse(false, false, null, message, List.of());
    }

    private MonitorStatusResponse response(
            boolean available, boolean stale, CacheEntry cached, String message) {
        return new MonitorStatusResponse(
                available, stale, cached.observedAt(), message, cached.monitors());
    }

    public record MonitorStatusResponse(
            boolean available,
            boolean stale,
            Instant observedAt,
            String message,
            List<MonitorSummary> monitors) {
    }

    public record MonitorSummary(
            String id,
            String name,
            String url,
            String status,
            Instant lastCheckedAt) {
    }

    private record CacheEntry(Instant observedAt, List<MonitorSummary> monitors) {
    }

    private static final class ProviderUnavailableException extends RuntimeException {
    }

    private static final class InvalidProviderResponseException extends RuntimeException {
    }
}
