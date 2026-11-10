package ma.ensa.apigateway.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Everything the gateway needs in order to decide whether an incoming token is acceptable.
 * Prefix: provider.jwt.*
 */
@ConfigurationProperties(prefix = "provider.jwt")
public record JwtProperties(
        // the realm incoming tokens are expected to come from
        String realm,
        // normally derived from provider.url + /realms/{realm}; set it only to override
        String issuer,
        // empty means "do not check aud" - Keycloak often issues aud=account,
        // so only turn this on once your clients have an audience mapper
        Set<String> audiences,
        // signature algorithms the gateway accepts. Never widen this to include "none".
        Set<String> allowedAlgorithms,
        // the "typ" claim an access token must carry; blank disables the check
        String tokenType,
        // tolerance for clock drift between the gateway and Keycloak
        Duration clockSkew,
        // how long a fetched JWKS stays fresh
        Duration jwksTtl,
        // floor between two JWKS fetches, so an unknown-kid flood cannot turn into
        // a request flood against Keycloak
        Duration jwksMinRefreshInterval,
        // request paths served without a token
        List<String> publicPaths
) {

    public JwtProperties {
        if (realm == null || realm.isBlank()) {
            throw new IllegalArgumentException("provider.jwt.realm must be set");
        }
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        allowedAlgorithms = (allowedAlgorithms == null || allowedAlgorithms.isEmpty())
                ? Set.of("RS256")
                : Set.copyOf(allowedAlgorithms);
        tokenType = tokenType == null ? "Bearer" : tokenType;
        clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
        jwksTtl = jwksTtl == null ? Duration.ofMinutes(10) : jwksTtl;
        jwksMinRefreshInterval = jwksMinRefreshInterval == null ? Duration.ofSeconds(30) : jwksMinRefreshInterval;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    /**
     * The exact string the "iss" claim has to equal.
     */
    public String resolvedIssuer(String providerUrl) {
        if (issuer != null && !issuer.isBlank()) {
            return issuer;
        }
        String base = providerUrl == null ? "" : providerUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/realms/" + realm;
    }
}
