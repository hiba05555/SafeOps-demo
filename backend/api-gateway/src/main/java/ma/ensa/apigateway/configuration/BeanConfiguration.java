package ma.ensa.apigateway.configuration;


import ma.ensa.apigateway.properties.AuthProviderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfiguration {
//    private final AuthProviderProperties properties;
//
//    public BeanConfiguration(AuthProviderProperties properties) {
//        this.properties = properties;
//    }

    @Bean
    public RestClient getKeycloackRestClient(RestClient.Builder builder, AuthProviderProperties properties) {
        return builder
                .baseUrl(properties.url())
                .build();


    }
}
