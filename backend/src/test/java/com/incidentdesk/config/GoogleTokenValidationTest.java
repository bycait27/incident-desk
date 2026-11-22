package com.incidentdesk.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenValidationTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String ISSUER = "https://accounts.google.com";
    private static KeyPair trustedKeyPair;

    @BeforeAll
    static void generateSigningKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKeyPair = generator.generateKeyPair();
    }

    @Test
    void rejectsJwtSignedByAnUntrustedKey() throws Exception {
        KeyPair untrustedKeyPair = newRsaKeyPair();
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) trustedKeyPair.getPublic())
                .build();

        assertThatThrownBy(() -> decoder.decode(signedToken((RSAPrivateKey) untrustedKeyPair.getPrivate())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void registersGoogleIssuerAndJwksForOidcValidation() {
        ClientRegistrationRepository registrations =
                new GoogleOAuth2ClientConfig().googleClientRegistrationRepository(CLIENT_ID, "test-secret");
        ClientRegistration registration = registrations.findByRegistrationId("google");

        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(ISSUER);
        assertThat(registration.getProviderDetails().getJwkSetUri())
                .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        assertThat(registration.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void rejectsIdTokenWithWrongIssuer() {
        assertThat(idTokenValidator().validate(idToken("https://attacker.example", CLIENT_ID, futureExpiry()))
                .hasErrors()).isTrue();
    }

    @Test
    void rejectsIdTokenWithWrongAudience() {
        assertThat(idTokenValidator().validate(idToken(ISSUER, "another-client", futureExpiry()))
                .hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredIdToken() {
        assertThat(idTokenValidator().validate(idToken(ISSUER, CLIENT_ID, Instant.now().minusSeconds(300)))
                .hasErrors()).isTrue();
    }

    @Test
    void rejectsAccessTokenMissingRequiredScope() {
        OAuth2AccessToken accessToken = accessToken(Set.of("openid", "email"));

        assertThat(new GoogleGrantedScopesValidator().validate(accessToken).hasErrors()).isTrue();
    }

    @Test
    void acceptsGoogleAliasesForEmailAndProfileScopes() {
        OAuth2AccessToken accessToken = accessToken(Set.of(
                "openid",
                GoogleGrantedScopesValidator.EMAIL_SCOPE,
                GoogleGrantedScopesValidator.PROFILE_SCOPE));

        assertThat(new GoogleGrantedScopesValidator().validate(accessToken).hasErrors()).isFalse();
    }

    private static OidcIdTokenValidator idTokenValidator() {
        ClientRegistrationRepository registrations =
                new GoogleOAuth2ClientConfig().googleClientRegistrationRepository(CLIENT_ID, "test-secret");
        ClientRegistration registration = registrations.findByRegistrationId("google");
        return new OidcIdTokenValidator(registration);
    }

    private static Jwt idToken(String issuer, String audience, Instant expiresAt) {
        Instant now = Instant.now();
        Instant issuedAt = expiresAt.isBefore(now) ? expiresAt.minusSeconds(300) : now.minusSeconds(30);
        return Jwt.withTokenValue("synthetic-id-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("synthetic-subject")
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private static Instant futureExpiry() {
        return Instant.now().plusSeconds(300);
    }

    private static OAuth2AccessToken accessToken(Set<String> scopes) {
        Instant issuedAt = Instant.now();
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "synthetic-access-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                scopes);
    }

    private static String signedToken(RSAPrivateKey privateKey) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("synthetic-subject")
                .audience(CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner(privateKey));
        return token.serialize();
    }

    private static KeyPair newRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
