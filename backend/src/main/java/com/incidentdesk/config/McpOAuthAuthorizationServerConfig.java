package com.incidentdesk.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp.oauth", name = "enabled", havingValue = "true")
public class McpOAuthAuthorizationServerConfig {

    private static final Set<String> MCP_SCOPES = Set.of(
            "incidents:read",
            "monitoring:read",
            "incidents:submit",
            "mcp:audit");

    @Bean
    @Order(1)
    SecurityFilterChain mcpAuthorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.cors(Customizer.withDefaults());
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    RegisteredClientRepository mcpRegisteredClientRepository(
            @Value("${app.mcp.oauth.client-id}") String clientId,
            @Value("${app.mcp.oauth.redirect-uris}") String redirectUris) {
        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName("Incident Desk MCP Host")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build());

        List<String> configuredRedirectUris = List.of(redirectUris.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (configuredRedirectUris.isEmpty()) {
            throw new IllegalStateException("At least one MCP OAuth redirect URI is required.");
        }
        configuredRedirectUris.forEach(uri -> {
            validateRedirectUri(uri);
            client.redirectUri(uri);
        });
        MCP_SCOPES.forEach(client::scope);
        return new InMemoryRegisteredClientRepository(client.build());
    }

    @Bean
    OAuth2AuthorizationService mcpOAuth2AuthorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    OAuth2AuthorizationConsentService mcpOAuth2AuthorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    @Bean
    JWKSource<SecurityContext> mcpJwkSource(
            @Value("${app.mcp.oauth.signing-jwk-base64:}") String encodedJwk) {
        if (!StringUtils.hasText(encodedJwk)) {
            throw new IllegalStateException(
                    "MCP_SIGNING_JWK_BASE64 must contain a private signing JWK when MCP OAuth is enabled.");
        }
        try {
            String json = new String(Base64.getDecoder().decode(encodedJwk), StandardCharsets.UTF_8);
            JWK jwk = JWK.parse(json);
            if (!(jwk instanceof RSAKey rsaKey) || !rsaKey.isPrivate()
                    || !JWSAlgorithm.RS256.equals(rsaKey.getAlgorithm())) {
                throw new IllegalArgumentException("The MCP signing JWK must be a private RS256 RSA key.");
            }
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (Exception exception) {
            throw new IllegalStateException("MCP_SIGNING_JWK_BASE64 is not a valid private RSA JWK.", exception);
        }
    }

    @Bean
    JwtDecoder mcpJwtDecoder(
            JWKSource<SecurityContext> mcpJwkSource,
            @Value("${app.mcp.oauth.issuer}") String issuer,
            @Value("${app.mcp.resource-url}") String resourceUrl) {
        JwtDecoder decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(mcpJwkSource);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(resourceUrl)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "The token audience is not this MCP resource.", null));
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder) decoder).setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audienceValidator));
        return decoder;
    }

    @Bean
    AuthorizationServerSettings mcpAuthorizationServerSettings(
            @Value("${app.mcp.oauth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> mcpAccessTokenCustomizer(
            @Value("${app.mcp.resource-url}") String resourceUrl) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            context.getClaims().audience(List.of(resourceUrl));
            context.getClaims().claim(
                    "roles",
                    AuthorityUtils.authorityListToSet(context.getPrincipal().getAuthorities()));
            Object principal = context.getPrincipal().getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                context.getClaims().claim("sub", oidcUser.getSubject());
                if (StringUtils.hasText(oidcUser.getEmail())) {
                    context.getClaims().claim("email", oidcUser.getEmail());
                }
            }
        };
    }

    private static void validateRedirectUri(String redirectUri) {
        URI uri = URI.create(redirectUri);
        boolean localHttp = "http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost())
                || "127.0.0.1".equals(uri.getHost())
                || "[::1]".equals(uri.getHost()));
        boolean secureHttps = "https".equals(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        if ((!localHttp && !secureHttps) || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "MCP OAuth redirect URIs must use HTTPS or a loopback HTTP address.");
        }
    }
}
