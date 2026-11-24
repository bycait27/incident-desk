package com.incidentdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RateLimitFilter extends OncePerRequestFilter {

    static final int LOGIN_REQUESTS_PER_MINUTE = 10;
    static final int WRITE_REQUESTS_PER_MINUTE = 30;
    static final int MONITOR_REQUESTS_PER_MINUTE = 10;

    private final ObjectMapper objectMapper;
    private final FixedWindowRateLimiter rateLimiter;

    public RateLimitFilter(ObjectMapper objectMapper, FixedWindowRateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = clientKey(request, limit.category());
        FixedWindowRateLimiter.Decision decision =
                rateLimiter.tryAcquire(clientKey, limit.requestsPerMinute(), System.nanoTime());
        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    new ApiErrorResponse("RATE_LIMITED", "Too many requests. Please retry later."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Limit limitFor(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/oauth2/authorization/")) {
            return new Limit("login-start", LOGIN_REQUESTS_PER_MINUTE);
        }
        if (path.startsWith("/login/oauth2/code/")) {
            return new Limit("login-callback", LOGIN_REQUESTS_PER_MINUTE);
        }
        if ("GET".equals(request.getMethod())
                && ("/api/uptime/monitors".equals(path) || "/api/mcp/uptime".equals(path))) {
            return new Limit("monitor", MONITOR_REQUESTS_PER_MINUTE);
        }
        if (isWriteMethod(request.getMethod()) && isWritePath(path)) {
            return new Limit("write", WRITE_REQUESTS_PER_MINUTE);
        }
        return null;
    }

    private String clientKey(HttpServletRequest request, String category) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            remoteAddress = "unknown";
        }
        return category + "|ip:" + remoteAddress;
    }

    private boolean isWriteMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isWritePath(String path) {
        return "/api/incidents".equals(path)
                || path.startsWith("/api/incidents/")
                || "/api/mcp/incidents".equals(path)
                || "/api/mcp/tool-audit".equals(path);
    }

    private record Limit(String category, int requestsPerMinute) {
    }
}
