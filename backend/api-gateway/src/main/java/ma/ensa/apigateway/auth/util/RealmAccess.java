package ma.ensa.apigateway.auth.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

/**
 * The "realm_access" claim of a Keycloak access token: realm-wide roles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RealmAccess(
        Set<String> roles
) {
    public Set<String> roles() {
        return roles == null ? Set.of() : roles;
    }
}
