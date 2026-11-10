package ma.ensa.apigateway.auth;

import ma.ensa.apigateway.auth.util.Jwk;
import ma.ensa.apigateway.auth.util.Jwks;
import ma.ensa.apigateway.properties.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the realm's signing keys in memory and handles key rotation.
 * <p>
 * Keycloak rotates its realm keys: a token can arrive signed with a "kid" the gateway has
 * never seen. The rule implemented here is the standard one:
 * <ul>
 *   <li>serve the cached JWKS as long as it is fresh and contains the requested kid;</li>
 *   <li>on an unknown kid, refetch once - that is what a rotation looks like from here;</li>
 *   <li>never refetch more often than {@code jwksMinRefreshInterval}, otherwise anyone can
 *       turn a stream of forged kids into a request flood against Keycloak;</li>
 *   <li>keep serving a stale snapshot if a refresh fails, rather than rejecting every request
 *       because the identity provider blinked.</li>
 * </ul>
 * This class is a singleton bean read from many request threads, so the cached value is a
 * single immutable snapshot swapped atomically, and only one thread refreshes at a time.
 */
@Component
public class JwksProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksProvider.class);

    private final RestClient restClient;
    private final JwtProperties properties;
    private final Clock clock;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Snapshot snapshot;
    private volatile Instant lastFetchAttempt = Instant.EPOCH;

    @Autowired
    public JwksProvider(RestClient restClient, JwtProperties properties) {
        this(restClient, properties, Clock.systemUTC());
    }

    JwksProvider(RestClient restClient, JwtProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The signing keys to verify a token whose header carries this kid.
     * Guarantees the returned Jwks contains the kid, so the caller never has to deal with a miss.
     */
    public Jwks jwksFor(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new InvalidTokenException("token header has no kid");
        }

        Snapshot current = snapshot;
        if (usable(current, kid)) {
            return current.jwks();
        }

        refreshLock.lock();
        try {
            // another thread may have refreshed while this one waited for the lock
            current = snapshot;
            if (usable(current, kid)) {
                return current.jwks();
            }
            if (!mayFetchNow() && current != null) {
                // rate limited: either the kid is forged, or rotation happened seconds ago
                // and the next caller will pick up the new keys
                throw new InvalidTokenException("unknown key id " + kid);
            }
            Snapshot fetched = fetch();
            if (fetched != null) {
                snapshot = fetched;
                current = fetched;
            }
        } finally {
            refreshLock.unlock();
        }

        if (current == null) {
            throw new InvalidTokenException("signing keys are not available");
        }
        if (!current.kids().contains(kid)) {
            throw new InvalidTokenException("unknown key id " + kid);
        }
        return current.jwks();
    }

    /**
     * Convenience for callers that want the key itself rather than the set.
     */
    public Jwk jwk(String kid) {
        return jwksFor(kid).keys().stream()
                .filter(jwk -> kid.equals(jwk.kid()))
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("unknown key id " + kid));
    }

    /**
     * Drops the cache; the next verification refetches. Useful after a known rotation.
     */
    public void invalidate() {
        snapshot = null;
    }

    public Set<String> cachedKids() {
        Snapshot current = snapshot;
        return current == null ? Set.of() : current.kids();
    }

    private boolean usable(Snapshot candidate, String kid) {
        return candidate != null
                && candidate.kids().contains(kid)
                && !candidate.isExpired(clock.instant(), properties.jwksTtl());
    }

    private boolean mayFetchNow() {
        return Duration.between(lastFetchAttempt, clock.instant())
                .compareTo(properties.jwksMinRefreshInterval()) >= 0;
    }

    private Snapshot fetch() {
        lastFetchAttempt = clock.instant();
        try {
            Jwks fetched = restClient
                    .get()
                    .uri("/realms/{realm}/protocol/openid-connect/certs", properties.realm())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Jwks.class);

            Jwks verificationKeys = onlySignatureKeys(fetched);
            if (verificationKeys.keys().isEmpty()) {
                log.warn("JWKS of realm {} contains no usable signature key", properties.realm());
                return null;
            }
            Set<String> kids = new LinkedHashSet<>();
            verificationKeys.keys().forEach(jwk -> kids.add(jwk.kid()));
            log.debug("fetched {} signature key(s) for realm {}: {}",
                    kids.size(), properties.realm(), kids);
            return new Snapshot(verificationKeys, Set.copyOf(kids), clock.instant());

        } catch (RestClientException e) {
            // the identity provider is unreachable; keep serving what is already cached
            log.warn("could not refresh JWKS of realm {}: {}", properties.realm(), e.getMessage());
            return null;
        }
    }

    /**
     * Keycloak publishes encryption keys (use=enc, alg=RSA-OAEP) next to the signing keys.
     * Only keys that can legitimately have produced an accepted signature are kept.
     */
    private Jwks onlySignatureKeys(Jwks fetched) {
        if (fetched == null || fetched.keys() == null) {
            return new Jwks(List.of());
        }
        return new Jwks(fetched.keys().stream()
                .filter(jwk -> jwk.kid() != null && jwk.n() != null && jwk.e() != null)
                .filter(jwk -> "RSA".equals(jwk.kty()))
                .filter(jwk -> jwk.use() == null || "sig".equals(jwk.use()))
                .filter(jwk -> jwk.alg() == null || properties.allowedAlgorithms().contains(jwk.alg()))
                .toList());
    }

    private record Snapshot(Jwks jwks, Set<String> kids, Instant fetchedAt) {
        private Snapshot {
            Objects.requireNonNull(jwks);
        }

        boolean isExpired(Instant now, Duration ttl) {
            return Duration.between(fetchedAt, now).compareTo(ttl) > 0;
        }
    }
}
