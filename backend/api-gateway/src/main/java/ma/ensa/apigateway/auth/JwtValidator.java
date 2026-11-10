package ma.ensa.apigateway.auth;

import ma.ensa.apigateway.auth.util.Jwt;
import ma.ensa.apigateway.auth.util.JwtHeader;
import ma.ensa.apigateway.auth.util.JwtPayload;
import ma.ensa.apigateway.properties.AuthProviderProperties;
import ma.ensa.apigateway.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * The checks a valid signature does not perform.
 * <p>
 * A signature only proves who wrote the token. It says nothing about whether the token is
 * still valid, whether it was meant for this gateway, or whether it is an access token at all.
 */
@Component
public class JwtValidator {

    private final JwtProperties properties;
    private final AuthProviderProperties providerProperties;
    private final Clock clock;

    @Autowired
    public JwtValidator(JwtProperties properties, AuthProviderProperties providerProperties) {
        this(properties, providerProperties, Clock.systemUTC());
    }

    JwtValidator(JwtProperties properties, AuthProviderProperties providerProperties, Clock clock) {
        this.properties = properties;
        this.providerProperties = providerProperties;
        this.clock = clock;
    }

    /**
     * Checked BEFORE the signature, because it decides which algorithm is allowed to run.
     * Taking the algorithm from the token itself is the "alg: none" / algorithm-confusion
     * attack: the header is attacker-controlled, so it may only be matched against a fixed
     * whitelist, never obeyed.
     */
    public void validateHeader(Jwt jwt) {
        JwtHeader header = jwt.header();
        if (header == null) {
            throw new InvalidTokenException("token has no header");
        }
        if (header.alg() == null || !properties.allowedAlgorithms().contains(header.alg())) {
            throw new InvalidTokenException("algorithm " + header.alg() + " is not accepted");
        }
        if (header.kid() == null || header.kid().isBlank()) {
            throw new InvalidTokenException("token header has no kid");
        }
    }

    /**
     * Checked AFTER the signature: until the signature holds, the claims are just text
     * someone sent us.
     */
    public void validateClaims(Jwt jwt) {
        JwtPayload payload = jwt.payload();
        if (payload == null) {
            throw new InvalidTokenException("token has no payload");
        }

        Instant now = clock.instant();
        long skew = properties.clockSkew().toSeconds();

        if (payload.sub() == null || payload.sub().isBlank()) {
            throw new InvalidTokenException("token has no subject");
        }

        String expectedIssuer = properties.resolvedIssuer(providerProperties.url());
        if (!expectedIssuer.equals(payload.iss())) {
            throw new InvalidTokenException("issuer " + payload.iss() + " is not " + expectedIssuer);
        }

        if (!properties.tokenType().isBlank()
                && payload.typ() != null
                && !properties.tokenType().equalsIgnoreCase(payload.typ())) {
            // refuses refresh tokens and id tokens presented as access tokens
            throw new InvalidTokenException("token type " + payload.typ()
                    + " is not " + properties.tokenType());
        }

        if (payload.exp() == null) {
            throw new InvalidTokenException("token has no expiry");
        }
        if (now.getEpochSecond() > payload.exp() + skew) {
            throw new InvalidTokenException("token expired at " + Instant.ofEpochSecond(payload.exp()));
        }

        if (payload.nbf() != null && now.getEpochSecond() + skew < payload.nbf()) {
            throw new InvalidTokenException("token is not valid before " + Instant.ofEpochSecond(payload.nbf()));
        }

        if (payload.iat() != null && payload.iat() - skew > now.getEpochSecond()) {
            throw new InvalidTokenException("token was issued in the future");
        }

        Set<String> expectedAudiences = properties.audiences();
        if (!expectedAudiences.isEmpty() && Collections.disjoint(expectedAudiences, payload.aud())) {
            throw new InvalidTokenException("audience " + payload.aud() + " does not include any of "
                    + expectedAudiences);
        }
    }
}
