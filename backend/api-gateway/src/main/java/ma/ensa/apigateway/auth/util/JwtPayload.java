package ma.ensa.apigateway.auth.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JwtPayload(
        String sub,
        String iss,
        // exp / iat / nbf are JSON numbers (seconds since epoch), so they are typed as Long:
        // they have to be compared against the clock, not printed.
        Long exp,
        Long iat,
        Long nbf,
        String jti,
        // "Bearer" for an access token, "Refresh" for a refresh token, "ID" for an id token.
        String typ,
        // authorized party: the client the token was issued to.
        String azp,
        String sid,
        String scope,
        // "aud" is a single string for some tokens and an array for others,
        // see StringOrSetDeserializer in JwtAdapter.
        Set<String> aud,
        String name,
        String preferred_username,
        String email,
        // roles, the whole point of the token for a gateway.
        RealmAccess realm_access,
        Map<String, ResourceAccess> resource_access

) {
    public Set<String> aud() {
        return aud == null ? Set.of() : aud;
    }

    public Map<String, ResourceAccess> resource_access() {
        return resource_access == null ? Map.of() : resource_access;
    }
}
