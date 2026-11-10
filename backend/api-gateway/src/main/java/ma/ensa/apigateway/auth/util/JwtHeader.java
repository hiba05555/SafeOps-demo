package ma.ensa.apigateway.auth.util;

public record JwtHeader(
        String alg,
        String typ,
        String kid

) {
}
