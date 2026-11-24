package com.incidentdesk.controllers;

import java.util.List;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser(
            @AuthenticationPrincipal OidcUser user,
            Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.equals("ROLE_OPERATOR") || authority.equals("ROLE_VIEWER"))
                .toList();
        return new CurrentUserResponse(user.getSubject(), user.getEmail(), roles);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse getCsrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }

    public record CurrentUserResponse(String subject, String email, List<String> roles) {
    }

    public record CsrfTokenResponse(String token) {
    }
}
