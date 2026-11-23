package com.incidentdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp.oauth", name = "enabled", havingValue = "true")
public class McpResourceServerSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain mcpResourceServerSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper) throws Exception {
        http
                .securityMatcher("/api/mcp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/mcp/incidents", "/api/mcp/incidents/**")
                        .hasAuthority("SCOPE_incidents:read")
                        .requestMatchers(HttpMethod.GET, "/api/mcp/uptime")
                        .hasAuthority("SCOPE_monitoring:read")
                        .requestMatchers(HttpMethod.POST, "/api/mcp/incidents")
                        .hasAuthority("SCOPE_incidents:submit")
                        .requestMatchers(HttpMethod.POST, "/api/mcp/tool-audit")
                        .hasAuthority("SCOPE_mcp:audit")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, error) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED", "A valid MCP access token is required."))
                        .accessDeniedHandler((request, response, error) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                        "INSUFFICIENT_SCOPE", "The access token lacks the required scope.")));
        return http.build();
    }

    private static void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message));
    }
}
