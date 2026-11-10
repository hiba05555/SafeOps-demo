package ma.ensa.apigateway.configuration;

import ma.ensa.apigateway.auth.util.Jwks;
import ma.ensa.apigateway.auth.util.Jwt;
import ma.ensa.apigateway.auth.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * provider.self-test=true: at startup, get a token for the gateway's own client
 * (client_credentials) and check its signature against the realm keys. Proves the client
 * secret written by keycloak-init is the right one. A failure is logged, never fatal.
 */
@Configuration
@ConditionalOnProperty(name = "provider.self-test", havingValue = "true")
public class SelfTestConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SelfTestConfiguration.class);

    @Bean
    CommandLineRunner keycloakSelfTest(JwtUtil jwtUtil) {
        return args -> {
            try {
                Jwt jwt = jwtUtil.getJwt();
                Jwks jwks = jwtUtil.getJwks();
                log.info("keycloak self-test: client token for {} issued by {}, signature valid = {}",
                        jwt.payload().azp(), jwt.payload().iss(), jwtUtil.verifyToken(jwt, jwks));
            } catch (RuntimeException e) {
                log.warn("keycloak self-test failed: {}", e.getMessage());
            }
        };
    }
}
