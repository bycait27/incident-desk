package com.incidentdesk.controllers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserControllerTest {

    @Test
    void reportsMappedAuthenticationRoleAlongsideValidatedOidcIdentity() {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn("synthetic-subject");
        when(user.getEmail()).thenReturn("operator@example.test");
        var authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

        CurrentUserController.CurrentUserResponse response =
                new CurrentUserController().getCurrentUser(user, authentication);

        assertThat(response.subject()).isEqualTo("synthetic-subject");
        assertThat(response.email()).isEqualTo("operator@example.test");
        assertThat(response.roles()).containsExactly("ROLE_OPERATOR");
    }
}
