package com.incidentdesk.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GoogleOAuth2ClientConfigTest {

    @Test
    void requiresServerSideCredentialsWhenGoogleLoginIsEnabled() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GoogleOAuth2ClientConfig()
                        .googleClientRegistrationRepository("", ""));
    }

    @Test
    void authorizationRequestIncludesStateNonceAndPkceChallenge() {
        ClientRegistrationRepository registrations =
                new GoogleOAuth2ClientConfig().googleClientRegistrationRepository(
                        "unit-test-client-id",
                        "not-a-real-client-secret");
        OAuth2AuthorizationRequestResolver resolver =
                SecurityConfig.googleAuthorizationRequestResolver(registrations);
        MockHttpServletRequest httpRequest =
                new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        httpRequest.setServletPath("/oauth2/authorization/google");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(httpRequest);

        assertThat(authorizationRequest).isNotNull();
        var query = UriComponentsBuilder
                .fromUriString(authorizationRequest.getAuthorizationRequestUri())
                .build()
                .getQueryParams();

        assertThat(query.getFirst("response_type")).isEqualTo("code");
        assertThat(query.getFirst("state")).isNotBlank();
        assertThat(query.getFirst("nonce")).isNotBlank();
        assertThat(query.getFirst("code_challenge")).isNotBlank();
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
    }
}
