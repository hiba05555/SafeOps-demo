package ma.ensa.apigateway.auth.util;

import ma.ensa.apigateway.properties.AuthProviderProperties;
import ma.ensa.apigateway.properties.JwtProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

@Component
public class JwtUtil {
    private final RestClient restClient;
    private final AuthProviderProperties properties;
    private final JwtAdapter jwtAdapter;
    private final JwtProperties jwtProperties;
//    private String access_token; the problem here is that this mutable attribute will change whenever this component is called
//                                  and that will cause concurrency issues (no mutablility for this bean scope ) use method parameters instead and call from upper layers.
//    private String[] part = {};


    public JwtUtil(RestClient restClient, AuthProviderProperties properties, JwtAdapter jwtAdapter,
                   JwtProperties jwtProperties) {
        this.restClient = restClient;
        this.properties = properties;
        this.jwtProperties = jwtProperties;

        this.jwtAdapter = jwtAdapter;
    }

    /**
     * A client_credentials token for the gateway's own client (provider.client-id / client-secret).
     */
    public Jwt getJwt(){
        if (properties.clientId() == null || properties.clientSecret() == null) {
            throw new IllegalStateException("provider.client-id and provider.client-secret must be set");
        }
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", properties.clientId());
        formData.add("grant_type","client_credentials");
        formData.add("client_secret", properties.clientSecret());
        TokenDto tokenDto =  restClient
                .post()
                .uri("/realms/{realm}/protocol/openid-connect/token", jwtProperties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TokenDto.class);
        return jwtAdapter.buildJwt(tokenDto);



    }
    public Jwks getJwks(){
        return restClient
                .get()
                .uri("/realms/{realm}/protocol/openid-connect/certs", jwtProperties.realm())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Jwks.class);
    }
    public Jwk getJwk(String kid, Jwks jwks){
        return jwks.keys().stream().
                filter(jwk -> jwk.kid().equals(kid)).
                findFirst().
                orElseThrow(()-> new IllegalArgumentException("kid or jwks not valid"));
    }

//    private Jwt buildJwt(String access_token){
//        if(access_token!=null){
//        String[] parts = access_token.split("\\.");
//        return new Jwt(new String(Base64.getUrlDecoder().decode(parts[0])),
//                new String(Base64.getUrlDecoder().decode(parts[1])),
//                new String(Base64.getUrlDecoder().decode(parts[2])));
//        }
//        else {throw new IllegalArgumentException("invalid access_token");}
//
//    }

    public boolean verifyToken(Jwt jwt,Jwks jwks){
        Jwk jwk = getJwk(jwt.header().kid(),jwks);
        byte[] signature = jwt.signature();
        byte[] data = jwt.signedInput().getBytes(StandardCharsets.US_ASCII);
        byte[] bytemodulus = Base64.getUrlDecoder().decode(jwk.n());
        byte[] bytexponent = Base64.getUrlDecoder().decode(jwk.e());
        BigInteger modulus = new BigInteger(1,bytemodulus);
        BigInteger exponent = new BigInteger(1,bytexponent);

        KeySpec keySpec = new RSAPublicKeySpec(modulus,exponent);
        try {
            PublicKey publicKey= KeyFactory.getInstance("RSA").generatePublic(keySpec);
            Signature instance = Signature.getInstance("SHA256withRSA");
            instance.initVerify(publicKey);
            instance.update(data);

            return instance.verify(signature);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

    }
}
