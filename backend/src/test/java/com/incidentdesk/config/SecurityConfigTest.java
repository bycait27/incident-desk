package com.incidentdesk.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class SecurityConfigTest {

    @Test
    void oauthSuccessRedirectsToAppInsteadOfSavedApiRequest() throws IOException, ServletException {
        String successUrl = "https://status.example.test/?login=success";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setQueryString("continue");
        request.setSession(new MockHttpSession());
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = new TestingAuthenticationToken(
                "synthetic-viewer", "password", "ROLE_VIEWER");
        AuthenticationSuccessHandler handler = SecurityConfig.appSuccessHandler(successUrl);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(successUrl, response.getRedirectedUrl());
    }
}
