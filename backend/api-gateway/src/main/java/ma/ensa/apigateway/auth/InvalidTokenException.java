package ma.ensa.apigateway.auth;

/**
 * Every reason a token can be refused: malformed, wrong algorithm, bad signature,
 * expired, wrong issuer, unknown key. The message is for the gateway log, not for the
 * caller - the response body only ever says "invalid_token".
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
