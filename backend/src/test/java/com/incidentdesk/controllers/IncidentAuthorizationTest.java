package com.incidentdesk.controllers;

import com.incidentdesk.config.SecurityConfig;
import com.incidentdesk.config.GoogleOAuth2ClientConfig;
import com.incidentdesk.services.BetterStackMonitorService;
import com.incidentdesk.services.BetterStackMonitorService.MonitorStatusResponse;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.IncidentEventStreamService;
import com.incidentdesk.services.IncidentService;
import com.incidentdesk.services.IncidentService.IncidentSnapshot;
import com.incidentdesk.services.IncidentConflictException;
import com.incidentdesk.dto.IncidentUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest({
        IncidentController.class,
        CurrentUserController.class,
        BetterStackMonitorController.class,
        IncidentEventStreamController.class,
        HealthController.class
})
@Import({SecurityConfig.class, GoogleOAuth2ClientConfig.class})
@TestPropertySource(properties = {
        "app.oauth.google.enabled=true",
        "app.oauth.google.client-id=synthetic-client",
        "app.oauth.google.client-secret=synthetic-secret",
        "app.oauth.google.operator-subject=operator-subject"
})
class IncidentAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private BetterStackMonitorService betterStackMonitorService;

    @MockBean
    private IncidentEventStreamService incidentEventStreamService;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthCheckIsPublicAndReportsDatabaseReadiness() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void appEntryPointIsNotBlockedByAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void secureResponsesIncludeRequiredSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(operatorLogin()).secure(true))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void healthCheckFailsWhenDatabaseIsUnavailable() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    @Test
    void incidentSnapshotExposesCursorForSseResume() throws Exception {
        when(incidentService.getIncidentSnapshot(false))
                .thenReturn(new IncidentSnapshot(List.of(), 12L));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Incident-Event-Cursor", "12"));
    }

    @Test
    void operatorSnapshotIncludesUnpublishedIncidents() throws Exception {
        Incident pending = new Incident();
        pending.setId(19L);
        pending.setTitle("Synthetic private incident");
        pending.setPublished(false);
        when(incidentService.getIncidentSnapshot(true))
                .thenReturn(new IncidentSnapshot(List.of(pending), 19L));

        mockMvc.perform(get("/api/incidents").with(operatorLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Synthetic private incident"))
                .andExpect(jsonPath("$[0].published").value(false));

        verify(incidentService).getIncidentSnapshot(true);
    }

    @Test
    void publicEventStreamResumesFromLastEventIdHeader() throws Exception {
        when(incidentEventStreamService.subscribe(13L, false)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/incidents/events")
                        .param("after", "12")
                        .header("Last-Event-ID", "13")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(request().asyncStarted());

        verify(incidentEventStreamService).subscribe(13L, false);
    }

    @Test
    void operatorEventStreamIncludesPrivateEvents() throws Exception {
        when(incidentEventStreamService.subscribe(19L, true)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/incidents/events")
                        .param("after", "19")
                        .with(operatorLogin())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(incidentEventStreamService).subscribe(19L, true);
    }

    @Test
    void publicMonitoringEndpointReturnsGracefulDegradationWithoutLogin() throws Exception {
        when(betterStackMonitorService.getMonitors()).thenReturn(
                new MonitorStatusResponse(false, false, null,
                        "Monitoring data is temporarily unavailable.", List.of()));

        mockMvc.perform(get("/api/uptime/monitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.monitors").isEmpty());
    }

    @Test
    void rejectsAnonymousWriteWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rateLimitsIncidentWritesWithRetryAfter() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/incidents")
                            .with(csrf())
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.24");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/incidents")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.24");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void viewerCannotUpdateIncidents() throws Exception {
        mockMvc.perform(put("/api/incidents/8")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("email", "viewer@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Synthetic incident","description":"Test","status":"OPEN","severity":"LOW"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void validatesViewerSubmissionOnTheServer() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("email", "viewer@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","description":"Synthetic"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void viewerCanSubmitPrivatelyAndActorComesFromOidcClaims() throws Exception {
        Incident created = new Incident();
        created.setId(19L);
        when(incidentService.createIncident("Synthetic incident", "Test description", "viewer@example.test"))
                .thenReturn(created);

        mockMvc.perform(post("/api/incidents")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "viewer-subject")
                                        .claim("email", "viewer@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf())
                        .header("X-User-Email", "attacker@example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Synthetic incident","description":"Test description"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/incidents/19"));

        verify(incidentService).createIncident(
                "Synthetic incident", "Test description", "viewer@example.test");
    }

    @Test
    void operatorCanPublishAndArchiveAnIncident() throws Exception {
        Incident published = new Incident();
        published.setId(8L);
        when(incidentService.publishIncident(8L, "operator@example.test")).thenReturn(published);

        mockMvc.perform(post("/api/incidents/8/publish")
                        .with(operatorLogin())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/incidents/8")
                        .with(operatorLogin())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsPublishingAnIncidentThatIsAlreadyPublic() throws Exception {
        when(incidentService.publishIncident(8L, "operator@example.test"))
                .thenThrow(new IncidentConflictException(8L));

        mockMvc.perform(post("/api/incidents/8/publish")
                        .with(operatorLogin())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void operatorCanUpdateAnIncident() throws Exception {
        when(incidentService.updateIncident(
                eq(8L), any(IncidentUpdateRequest.class), eq("operator@example.test")))
                .thenReturn(new Incident());

        mockMvc.perform(put("/api/incidents/8")
                        .with(operatorLogin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Synthetic incident","description":"Updated","status":"IN_PROGRESS","severity":"MEDIUM"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedUserCanSeeSubjectAndAssignedRole() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("sub", "viewer-subject")
                                        .claim("email", "viewer@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("viewer-subject"))
                .andExpect(jsonPath("$.email").value("viewer@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_VIEWER"));
    }

    @Test
    void authenticatedUserCanSignOut() throws Exception {
        mockMvc.perform(post("/logout").with(operatorLogin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatedUserCanFetchCsrfTokenForTheWriteForms() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").with(operatorLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .get("token").asText();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        Incident created = new Incident();
        created.setId(24L);
        when(incidentService.createIncident("CSRF test", "Synthetic", "operator@example.test"))
                .thenReturn(created);

        mockMvc.perform(post("/api/incidents")
                        .session(session)
                        .with(operatorLogin())
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"CSRF test","description":"Synthetic"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/incidents")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operatorLogin() {
        return oidcLogin()
                .idToken(token -> token.claim("sub", "operator-subject")
                        .claim("email", "operator@example.test"))
                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }
}
