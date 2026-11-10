package ma.ensa.apigateway.auth;

import ma.ensa.apigateway.auth.util.Jwt;
import ma.ensa.apigateway.auth.util.JwtAdapter;
import ma.ensa.apigateway.auth.util.JwtUtil;
import ma.ensa.apigateway.auth.util.Jwks;
import org.springframework.stereotype.Component;

/**
 * The whole verification in one place, in the only order that is safe:
 * <ol>
 *     <li>decode - structure only, nothing is trusted yet;</li>
 *     <li>check the header, which decides the algorithm;</li>
 *     <li>find the signing key for its kid, refetching the JWKS if the realm rotated;</li>
 *     <li>verify the signature (JwtUtil);</li>
 *     <li>only now read the claims;</li>
 *     <li>hand back the caller's identity and roles.</li>
 * </ol>
 * Stateless, so one singleton bean serves every request thread.
 */
@Component
public class TokenAuthenticator {

    private final JwtAdapter jwtAdapter;
    private final JwtValidator validator;
    private final JwksProvider jwksProvider;
    private final JwtUtil jwtUtil;

    public TokenAuthenticator(JwtAdapter jwtAdapter,
                              JwtValidator validator,
                              JwksProvider jwksProvider,
                              JwtUtil jwtUtil) {
        this.jwtAdapter = jwtAdapter;
        this.validator = validator;
        this.jwksProvider = jwksProvider;
        this.jwtUtil = jwtUtil;
    }

    /**
     * @param accessToken the raw compact JWS, with no "Bearer " prefix
     * @throws InvalidTokenException for every refusal, so the caller has one thing to catch
     */
    public AuthenticatedUser authenticate(String accessToken) {
        Jwt jwt = decode(accessToken);

        validator.validateHeader(jwt);

        Jwks jwks = jwksProvider.jwksFor(jwt.header().kid());
        if (!verify(jwt, jwks)) {
            throw new InvalidTokenException("signature does not match key " + jwt.header().kid());
        }

        validator.validateClaims(jwt);
        return AuthenticatedUser.from(jwt);
    }

    private Jwt decode(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidTokenException("no token presented");
        }
        try {
            return jwtAdapter.buildJwt(accessToken);
        } catch (RuntimeException e) {
            // bad base64, wrong number of parts, payload that is not JSON: all client errors
            throw new InvalidTokenException("token could not be decoded: " + e.getMessage(), e);
        }
    }

    private boolean verify(Jwt jwt, Jwks jwks) {
        try {
            return jwtUtil.verifyToken(jwt, jwks);
        } catch (InvalidTokenException e) {
            throw e;
        } catch (RuntimeException e) {
            // a key the JDK refuses, a signature of the wrong length: still the caller's fault,
            // so it must be a 401 and not a 500
            throw new InvalidTokenException("signature could not be verified: " + e.getMessage(), e);
        }
    }
}
