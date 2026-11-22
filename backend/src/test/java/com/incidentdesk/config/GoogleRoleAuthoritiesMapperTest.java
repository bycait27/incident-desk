package com.incidentdesk.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleRoleAuthoritiesMapperTest {

    @Test
    void assignsOperatorOnlyToTheAllowlistedValidatedSubject() {
        GoogleRoleAuthoritiesMapper mapper = new GoogleRoleAuthoritiesMapper("operator-subject");

        assertRole(mapper.mapAuthorities(List.of(oidcAuthority("operator-subject"))), "ROLE_OPERATOR");
        assertRole(mapper.mapAuthorities(List.of(oidcAuthority("viewer-subject"))), "ROLE_VIEWER");
    }

    @Test
    void defaultsToViewerWhenNoOperatorSubjectIsConfigured() {
        GoogleRoleAuthoritiesMapper mapper = new GoogleRoleAuthoritiesMapper("");

        assertRole(mapper.mapAuthorities(List.of(oidcAuthority("operator-subject"))), "ROLE_VIEWER");
    }

    @Test
    void defaultsToViewerWhenNoValidatedOidcSubjectIsPresent() {
        GoogleRoleAuthoritiesMapper mapper = new GoogleRoleAuthoritiesMapper("operator-subject");

        assertRole(mapper.mapAuthorities(List.of()), "ROLE_VIEWER");
    }

    private static void assertRole(
            Collection<? extends GrantedAuthority> authorities,
            String expectedRole) {
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains(expectedRole)
                .doesNotContain("ROLE_ADMIN");
    }

    private static OidcUserAuthority oidcAuthority(String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "synthetic-id-token",
                now,
                now.plusSeconds(300),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "sub", subject,
                        "aud", List.of("synthetic-client"),
                        "iat", now,
                        "exp", now.plusSeconds(300)));
        return new OidcUserAuthority(idToken);
    }
}
