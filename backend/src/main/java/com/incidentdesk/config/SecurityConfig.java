package com.incidentdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

import jakarta.servlet.http.HttpServletResponse;

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
            @Value("${app.oauth.google.enabled:false}") boolean googleLoginEnabled,
            @Value("${app.oauth.google.success-url:http://localhost:5173/?login=success}") String successUrl)
            throws Exception {
        http
            .cors(cors -> {}) // Enable CORS (respects your @CrossOrigin annotations)
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                        boolean anonymous = authentication == null
                                || AUTHENTICATION_TRUST_RESOLVER.isAnonymous(authentication);
                        response.sendError(anonymous
                                ? HttpServletResponse.SC_UNAUTHORIZED
                                : HttpServletResponse.SC_FORBIDDEN);
                    }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/incidents/**").permitAll()
                .requestMatchers("/api/incidents/**").hasRole("OPERATOR")
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
                    .defaultSuccessUrl(successUrl, true));
        }

        return http.build();
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
