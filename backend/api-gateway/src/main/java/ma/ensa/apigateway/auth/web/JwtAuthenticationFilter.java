package ma.ensa.apigateway.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.ensa.apigateway.auth.AuthenticatedUser;
import ma.ensa.apigateway.auth.InvalidTokenException;
import ma.ensa.apigateway.auth.TokenAuthenticator;
import ma.ensa.apigateway.properties.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The gate. Every request either carries a token this gateway can verify, or matches one of
 * provider.jwt.public-paths, or gets a 401 - and nothing reaches a downstream service
 * before that decision is made.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Where the verified caller is published for the rest of this request.
     */
    public static final String PRINCIPAL = AuthenticatedUser.class.getName();

    private static final String BEARER = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenAuthenticator authenticator;
    private final List<PathPattern> publicPaths;

    public JwtAuthenticationFilter(TokenAuthenticator authenticator, JwtProperties properties) {
        this.authenticator = authenticator;
        PathPatternParser parser = new PathPatternParser();
        this.publicPaths = properties.publicPaths().stream().map(parser::parse).toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (isPublic(request)) {
            // still wrapped: a public path must not be a way to smuggle in X-Auth-* headers
            chain.doFilter(new AuthenticatedRequest(request, null), response);
            return;
        }

        String token = bearerToken(request);
        try {
            AuthenticatedUser user = authenticator.authenticate(token);
            request.setAttribute(PRINCIPAL, user);
            chain.doFilter(new AuthenticatedRequest(request, user), response);

        } catch (InvalidTokenException e) {
            // the reason stays in the log; the caller is told no more than "invalid_token"
            log.debug("rejected {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            unauthorized(response, token == null ? null : "invalid_token");
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(pathWithinApplication(request));
        return publicPaths.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * RFC 6750: a missing token is answered with a bare challenge, a bad one with error=invalid_token.
     */
    private void unauthorized(HttpServletResponse response, String error) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                error == null ? "Bearer" : "Bearer error=\"" + error + "\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + (error == null ? "unauthorized" : error)
                + "\",\"error_description\":\"a valid bearer token is required\"}");
    }
}
