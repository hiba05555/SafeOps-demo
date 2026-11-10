package ma.ensa.apigateway.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import ma.ensa.apigateway.auth.AuthenticatedUser;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The request as the downstream services see it: the verified identity added as headers,
 * and any identity header the client tried to send removed.
 * <p>
 * That second part is the point. Downstream services trust X-Auth-* because the gateway is
 * the only way in, so a client that could set those headers itself would be able to claim any
 * identity. These names are therefore always answered from the token, never from the client,
 * whether the request was authenticated or not.
 */
public class AuthenticatedRequest extends HttpServletRequestWrapper {

    public static final String SUBJECT = "X-Auth-Subject";
    public static final String USERNAME = "X-Auth-Username";
    public static final String EMAIL = "X-Auth-Email";
    public static final String CLIENT = "X-Auth-Client";
    public static final String ROLES = "X-Auth-Roles";

    private static final Set<String> MANAGED = Set.of(
            SUBJECT.toLowerCase(), USERNAME.toLowerCase(), EMAIL.toLowerCase(),
            CLIENT.toLowerCase(), ROLES.toLowerCase());

    private final Map<String, String> identity;

    public AuthenticatedRequest(HttpServletRequest request, AuthenticatedUser user) {
        super(request);
        this.identity = identityHeaders(user);
    }

    private static Map<String, String> identityHeaders(AuthenticatedUser user) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (user == null) {
            return headers;
        }
        put(headers, SUBJECT, user.subject());
        put(headers, USERNAME, user.username());
        put(headers, EMAIL, user.email());
        put(headers, CLIENT, user.clientId());
        put(headers, ROLES, String.join(",", user.authorities()));
        return headers;
    }

    private static void put(Map<String, String> headers, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        // a value containing CR or LF would let a token claim inject a header of its own
        headers.put(name, value.replaceAll("[\\r\\n]", ""));
    }

    @Override
    public String getHeader(String name) {
        if (name != null && MANAGED.contains(name.toLowerCase())) {
            return identity.get(canonical(name));
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (name != null && MANAGED.contains(name.toLowerCase())) {
            String value = identity.get(canonical(name));
            return Collections.enumeration(value == null ? List.of() : List.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original != null && original.hasMoreElements()) {
            String name = original.nextElement();
            if (!MANAGED.contains(name.toLowerCase())) {
                names.add(name);
            }
        }
        names.addAll(identity.keySet());
        return Collections.enumeration(names);
    }

    private String canonical(String name) {
        for (String managed : identity.keySet()) {
            if (managed.equalsIgnoreCase(name)) {
                return managed;
            }
        }
        return name;
    }
}
