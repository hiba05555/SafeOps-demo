package ma.ensa.apigateway.auth.util;

import java.util.List;

public record Jwks(
        List<Jwk> keys
) {
}
