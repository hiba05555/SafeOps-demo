package ma.ensa.apigateway.auth;

import ma.ensa.apigateway.auth.util.Jwt;
import ma.ensa.apigateway.auth.util.JwtPayload;
import ma.ensa.apigateway.auth.util.ResourceAccess;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the gateway actually needs out of a verified token: who the caller is and what they
 * are allowed to do. Everything else in the token stays behind.
 * <p>
 * Keycloak splits roles in two: "realm_access.roles" are realm-wide, and
 * "resource_access.{clientId}.roles" are per client. Both are kept, because
 * flattening them loses the client they belong to - "admin" on realm-management is not
 * "admin" on your own service.
 */
public record AuthenticatedUser(
        String subject,
        String username,
        String email,
        String clientId,
        Set<String> realmRoles,
        Map<String, Set<String>> clientRoles,
        long expiresAt
) {

    public static AuthenticatedUser from(Jwt jwt) {
        JwtPayload payload = jwt.payload();

        Set<String> realmRoles = payload.realm_access() == null
                ? Set.of()
                : Set.copyOf(payload.realm_access().roles());

        Map<String, Set<String>> clientRoles = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceAccess> entry : payload.resource_access().entrySet()) {
            ResourceAccess access = entry.getValue();
            if (access != null && !access.roles().isEmpty()) {
                clientRoles.put(entry.getKey(), Set.copyOf(access.roles()));
            }
        }

        return new AuthenticatedUser(
                payload.sub(),
                payload.preferred_username(),
                payload.email(),
                payload.azp(),
                realmRoles,
                Map.copyOf(clientRoles),
                payload.exp() == null ? 0L : payload.exp());
    }

    public boolean hasRealmRole(String role) {
        return realmRoles.contains(role);
    }

    public boolean hasAnyRealmRole(String... roles) {
        for (String role : roles) {
            if (hasRealmRole(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasClientRole(String client, String role) {
        return clientRoles.getOrDefault(client, Set.of()).contains(role);
    }

    /**
     * Realm roles plain, client roles qualified as "clientId:role", so the two namespaces
     * cannot be confused with each other once flattened.
     */
    public Set<String> authorities() {
        Set<String> all = new LinkedHashSet<>(realmRoles);
        clientRoles.forEach((client, roles) -> roles.forEach(role -> all.add(client + ":" + role)));
        return all;
    }
}
