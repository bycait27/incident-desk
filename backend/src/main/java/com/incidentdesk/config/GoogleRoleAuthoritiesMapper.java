package com.incidentdesk.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.util.StringUtils;

final class GoogleRoleAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    private static final String ROLE_VIEWER = "ROLE_VIEWER";

    private final String operatorSubject;

    GoogleRoleAuthoritiesMapper(String operatorSubject) {
        this.operatorSubject = operatorSubject;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        String subject = authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(OidcUserAuthority.class::cast)
                .map(authority -> authority.getIdToken().getSubject())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");

        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        mapped.removeIf(authority -> authority.getAuthority().startsWith("ROLE_"));
        mapped.add(new SimpleGrantedAuthority(
                StringUtils.hasText(operatorSubject) && operatorSubject.equals(subject)
                        ? ROLE_OPERATOR
                        : ROLE_VIEWER));
        return mapped;
    }
}
