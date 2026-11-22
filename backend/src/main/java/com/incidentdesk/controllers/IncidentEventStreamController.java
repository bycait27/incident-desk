package com.incidentdesk.controllers;

import com.incidentdesk.services.IncidentEventStreamService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/incidents/events")
@CrossOrigin(origins = "*")
public class IncidentEventStreamController {

    private final IncidentEventStreamService eventStreamService;

    public IncidentEventStreamController(IncidentEventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) Long after) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(eventStreamService.subscribe(resolveCursor(lastEventId, after)));
    }

    private long resolveCursor(String lastEventId, Long after) {
        long queryCursor = after == null ? 0 : after;
        if (queryCursor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid incident event cursor.");
        }
        if (!StringUtils.hasText(lastEventId)) {
            return queryCursor;
        }
        try {
            long headerCursor = Long.parseLong(lastEventId);
            if (headerCursor < 0) {
                throw new NumberFormatException("Negative cursor");
            }
            return Math.max(queryCursor, headerCursor);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid incident event cursor.");
        }
    }
}
