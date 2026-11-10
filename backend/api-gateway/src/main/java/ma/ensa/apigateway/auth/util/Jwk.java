package ma.ensa.apigateway.auth.util;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Jwk(
        String kid,
        String kty,
        String alg,
        // "sig" or "enc": Keycloak publishes encryption keys on the same endpoint,
        // and they must never be used to check a signature.
        String use,
        String n,
        String e

) {

}
