package ma.ensa.apigateway.auth.util;


public record Jwt(
        JwtHeader header,
        JwtPayload payload,
        byte[] signature,
        // the exact "base64url(header).base64url(payload)" text as received,
        // it is what the RSA signature was computed over and cannot be rebuilt
        // from the decoded header/payload records.
        String signedInput
) {
}
