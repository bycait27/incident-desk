package com.incidentdesk.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void limitsLoginAttemptsAndReturnsRetryAfterError() throws Exception {
        RateLimitFilter filter = newFilter();
        for (int i = 0; i < RateLimitFilter.LOGIN_REQUESTS_PER_MINUTE; i++) {
            assertEquals(200, invoke(filter, request("GET", "/oauth2/authorization/google")).getStatus());
        }

        MockHttpServletResponse limited =
                invoke(filter, request("GET", "/oauth2/authorization/google"));

        assertRateLimited(limited);
    }

    @Test
    void limitsOAuthCallbacksSeparatelyFromLoginStarts() throws Exception {
        RateLimitFilter filter = newFilter();
        for (int i = 0; i < RateLimitFilter.LOGIN_REQUESTS_PER_MINUTE; i++) {
            assertEquals(200, invoke(filter, request("GET", "/oauth2/authorization/google")).getStatus());
            assertEquals(200, invoke(filter, request("GET", "/login/oauth2/code/google")).getStatus());
        }

        assertRateLimited(invoke(filter, request("GET", "/login/oauth2/code/google")));
    }

    @Test
    void limitsWriteActions() throws Exception {
        RateLimitFilter filter = newFilter();
        for (int i = 0; i < RateLimitFilter.WRITE_REQUESTS_PER_MINUTE; i++) {
            assertEquals(200, invoke(filter, request("POST", "/api/incidents")).getStatus());
        }

        assertRateLimited(invoke(filter, request("POST", "/api/incidents")));
    }

    @Test
    void limitsThirdPartyBackedMonitorReads() throws Exception {
        RateLimitFilter filter = newFilter();
        for (int i = 0; i < RateLimitFilter.MONITOR_REQUESTS_PER_MINUTE; i++) {
            assertEquals(200, invoke(filter, request("GET", "/api/mcp/uptime")).getStatus());
        }

        assertRateLimited(invoke(filter, request("GET", "/api/mcp/uptime")));
    }

    private RateLimitFilter newFilter() {
        return new RateLimitFilter(new ObjectMapper(), new FixedWindowRateLimiter());
    }

    private MockHttpServletResponse invoke(RateLimitFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(200);
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }

    private void assertRateLimited(MockHttpServletResponse response) throws IOException {
        assertEquals(429, response.getStatus());
        assertTrue(Integer.parseInt(response.getHeader("Retry-After")) > 0);
        assertEquals("RATE_LIMITED",
                new ObjectMapper().readTree(response.getContentAsByteArray()).get("code").asText());
    }
}
