package ma.ensa.apigateway.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import ma.ensa.apigateway.auth.AuthenticatedUser;
import ma.ensa.apigateway.auth.JwksProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A few endpoints to see the auth layer from the outside: who the gateway thinks you are,
 * what it forwards downstream, and which signing keys it currently holds.
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final JwksProvider jwksProvider;

    public AuthenticationController(JwksProvider jwksProvider) {
        this.jwksProvider = jwksProvider;
    }

    /**
     * The caller's identity as extracted from their token. Reaching this method at all means
     * the filter already verified the signature and the claims.
     */
    @GetMapping("/me")
    public AuthenticatedUser me(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(JwtAuthenticationFilter.PRINCIPAL);
    }

    /**
     * The X-Auth-* headers exactly as a downstream service receives them. Send a request with
     * your own X-Auth-Subject to see that it is replaced by the token's subject, never trusted.
     */
    @GetMapping("/forwarded")
    public Map<String, String> forwarded(HttpServletRequest request) {
        Map<String, String> forwarded = new LinkedHashMap<>();
        for (String name : List.of(AuthenticatedRequest.SUBJECT, AuthenticatedRequest.USERNAME,
                AuthenticatedRequest.EMAIL, AuthenticatedRequest.CLIENT, AuthenticatedRequest.ROLES)) {
            forwarded.put(name, request.getHeader(name));
        }
        return forwarded;
    }

    /**
     * The key ids currently cached. Handy to watch a realm key rotation happen.
     */
    @GetMapping("/keys")
    public Map<String, Object> keys() {
        return Map.of("cachedKids", jwksProvider.cachedKids());
    }

    /**
     * Forces the next request to refetch the JWKS.
     */
    @PostMapping("/keys/refresh")
    public ResponseEntity<Void> refreshKeys() {
        jwksProvider.invalidate();
        return ResponseEntity.noContent().build();
    }
}
