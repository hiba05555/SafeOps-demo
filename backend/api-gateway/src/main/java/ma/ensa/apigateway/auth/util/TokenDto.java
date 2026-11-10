package ma.ensa.apigateway.auth.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenDto(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") String exp,
        @JsonProperty("token_type") String tokentype
) {
}
