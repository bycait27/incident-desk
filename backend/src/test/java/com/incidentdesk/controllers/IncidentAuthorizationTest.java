package com.incidentdesk.controllers;

import com.incidentdesk.config.SecurityConfig;
import com.incidentdesk.config.GoogleOAuth2ClientConfig;
import com.incidentdesk.services.BetterStackMonitorService;
import com.incidentdesk.services.BetterStackMonitorService.MonitorStatusResponse;
import com.incidentdesk.entities.Incident;
import com.incidentdesk.services.IncidentEventStreamService;
import com.incidentdesk.services.IncidentService;
import com.incidentdesk.services.IncidentService.IncidentSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        IncidentEventStreamController.class
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

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private BetterStackMonitorService betterStackMonitorService;

    @MockBean
    private IncidentEventStreamService incidentEventStreamService;

    @Test
    void incidentSnapshotExposesCursorForSseResume() throws Exception {
        when(incidentService.getIncidentSnapshot())
                .thenReturn(new IncidentSnapshot(List.of(), 12L));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Incident-Event-Cursor", "12"));
    }

    @Test
    void publicEventStreamResumesFromLastEventIdHeader() throws Exception {
        when(incidentEventStreamService.subscribe(13L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/incidents/events")
                        .param("after", "12")
                        .header("Last-Event-ID", "13")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(request().asyncStarted());

        verify(incidentEventStreamService).subscribe(13L);
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsViewerWriteWithForbidden() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(user("viewer").roles("VIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanWriteAndActorComesFromOidcClaims() throws Exception {
        Incident created = new Incident();
        when(incidentService.createIncident(any(Incident.class), eq("operator@example.test")))
                .thenReturn(created);

        mockMvc.perform(post("/api/incidents")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "operator-subject")
                                        .claim("email", "operator@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .with(csrf())
                        .header("X-User-Email", "attacker@example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(incidentService).createIncident(any(Incident.class), eq("operator@example.test"));
    }

    @Test
    void authenticatedUserCanSeeSubjectAndAssignedRole() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin()
                                .idToken(token -> token.claim("sub", "viewer-subject"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("viewer-subject"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_VIEWER"));
    }
}
