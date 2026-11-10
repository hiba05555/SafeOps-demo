package ma.ensa.apigateway.auth.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

/**
 * One entry of the "resource_access" claim: the roles a user holds on a single client.
 * The client id is the map key in {@link JwtPayload#resource_access()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResourceAccess(
        Set<String> roles
) {
    public Set<String> roles() {
        return roles == null ? Set.of() : roles;
    }
}
