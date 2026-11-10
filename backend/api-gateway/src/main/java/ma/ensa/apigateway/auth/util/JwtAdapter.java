package ma.ensa.apigateway.auth.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtAdapter {
    // "aud" (and a few other JWT claims) are a bare string when there is one value
    // and an array when there are several. This accepts both shapes.
    private static final JsonDeserializer<Set<String>> STRING_OR_SET = (json, type, context) -> {
        Set<String> values = new LinkedHashSet<>();
        if (json == null || json.isJsonNull()) {
            return values;
        }
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> values.add(element.getAsString()));
        } else {
            values.add(json.getAsString());
        }
        return values;
    };

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<Set<String>>() {
            }.getType(), STRING_OR_SET)
            .create();

    public Jwt buildJwt(TokenDto tokenDto) {
        if (tokenDto == null) {
            throw new IllegalArgumentException("no token response to read");
        }
        return buildJwt(tokenDto.accessToken());
    }

    public Jwt buildJwt(String access_token) {
        if (access_token != null) {
            String[] parts = access_token.split("\\.");
            // a JWS is exactly three dot-separated parts; anything else is not a token we can check
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid access_token: expected 3 parts, got " + parts.length);
            }
            String headerString = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadString = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);


            JwtHeader jwtHeader = gson.fromJson(headerString, JwtHeader.class);
            JwtPayload jwtPayload = gson.fromJson(payloadString, JwtPayload.class);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (jwtHeader == null || jwtPayload == null) {
                throw new IllegalArgumentException("invalid access_token: header or payload is not an object");
            }
            return new Jwt(jwtHeader, jwtPayload, signature, parts[0] + "." + parts[1]);
        } else {
            throw new IllegalArgumentException("invalid access_token");
        }

    }


}
