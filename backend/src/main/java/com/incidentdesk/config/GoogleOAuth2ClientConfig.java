package com.incidentdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.Assert;

@Configuration
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${app.oauth.google.client-id}") String clientId,
            @Value("${app.oauth.google.client-secret}") String clientSecret) {
        Assert.hasText(clientId, "GOOGLE_CLIENT_ID is required when Google OAuth is enabled");
        Assert.hasText(clientSecret, "GOOGLE_CLIENT_SECRET is required when Google OAuth is enabled");

        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .clientName("Google")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> googleOidcUserService() {
        OidcUserService delegate = new OidcUserService();
        delegate.setAccessibleScopes(GoogleGrantedScopesValidator.ACCESSIBLE_SCOPES);
        GoogleGrantedScopesValidator scopesValidator = new GoogleGrantedScopesValidator();
        return request -> {
            var validation = scopesValidator.validate(request.getAccessToken());
            if (validation.hasErrors()) {
                throw new OAuth2AuthenticationException(validation.getErrors().iterator().next());
            }
            return delegate.loadUser(request);
        };
    }
}
