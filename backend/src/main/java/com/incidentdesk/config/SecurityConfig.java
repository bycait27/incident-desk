package com.incidentdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentdesk.dto.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final AuthenticationTrustResolver AUTHENTICATION_TRUST_RESOLVER =
            new AuthenticationTrustResolverImpl();

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> oidcUserServices,
            ObjectProvider<GrantedAuthoritiesMapper> authoritiesMappers,
            ObjectMapper objectMapper,
            @Value("${app.oauth.google.enabled:false}") boolean googleLoginEnabled,
            @Value("${app.oauth.google.success-url:http://localhost:5173/?login=success}") String successUrl)
            throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .logout(logout -> logout
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, authException) ->
                            writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED", "Authentication is required."))
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        boolean anonymous = authentication == null
                                || AUTHENTICATION_TRUST_RESOLVER.isAnonymous(authentication);
                        if (anonymous) {
                            writeError(response, objectMapper, HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED", "Authentication is required.");
                        } else {
                            writeError(response, objectMapper, HttpStatus.FORBIDDEN,
                                    "FORBIDDEN", "The current role cannot perform this action.");
                        }
                    }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/healthz").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/incidents/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/uptime/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/incidents").hasAnyRole("VIEWER", "OPERATOR")
                .requestMatchers("/api/incidents/*/publish").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.PUT, "/api/incidents/**").hasRole("OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/incidents/**").hasRole("OPERATOR")
                .requestMatchers("/api/incidents/**").denyAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .anyRequest().authenticated()
            );

        if (googleLoginEnabled) {
            OAuth2AuthorizationRequestResolver authorizationRequestResolver =
                    googleAuthorizationRequestResolver(clientRegistrations.getObject());
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestResolver(authorizationRequestResolver))
                    .userInfoEndpoint(userInfo ->
                            userInfo.oidcUserService(oidcUserServices.getObject())
                                    .userAuthoritiesMapper(authoritiesMappers.getObject()))
                    .defaultSuccessUrl(successUrl, false));
        }

        return http.build();
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin,
            @Value("${app.mcp.oauth.client-origin:}") String mcpClientOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin, mcpClientOrigin).stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .distinct()
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN", "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("X-Incident-Event-Cursor"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrations,
                        "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}
