package ma.ensa.apigateway.properties;


import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where Keycloak is, and the gateway's own confidential client.
 * clientSecret is never committed: the keycloak-init container reads it from Keycloak and
 * writes it to a properties file the gateway imports (see docker-compose.yml).
 */
@ConfigurationProperties(prefix = "provider")
public record AuthProviderProperties (
        @NotBlank
        String url,
        Long port,
        String clientId,
        String clientSecret) {}
