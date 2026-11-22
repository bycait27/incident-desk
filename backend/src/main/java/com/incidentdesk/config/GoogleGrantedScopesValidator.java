package com.incidentdesk.config;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

final class GoogleGrantedScopesValidator implements OAuth2TokenValidator<OAuth2AccessToken> {

    static final String EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email";
    static final String PROFILE_SCOPE = "https://www.googleapis.com/auth/userinfo.profile";
    static final Set<String> ACCESSIBLE_SCOPES =
            Set.of("email", "profile", EMAIL_SCOPE, PROFILE_SCOPE);

    private static final Set<String> REQUIRED_SCOPES = Set.of("openid", "email", "profile");
    private static final Map<String, String> GOOGLE_SCOPE_ALIASES = Map.of(
            EMAIL_SCOPE, "email",
            PROFILE_SCOPE, "profile");

    @Override
    public OAuth2TokenValidatorResult validate(OAuth2AccessToken token) {
        Set<String> grantedScopes = token.getScopes().stream()
                .map(scope -> GOOGLE_SCOPE_ALIASES.getOrDefault(scope, scope))
                .collect(Collectors.toSet());
        if (!grantedScopes.containsAll(REQUIRED_SCOPES)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INSUFFICIENT_SCOPE,
                    "Google did not grant the required OpenID Connect scopes",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
