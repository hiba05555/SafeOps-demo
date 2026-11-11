package ma.ensa.appointmentservice.security;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    static final String SUBJECT = "X-Auth-Subject";
    static final String USERNAME = "X-Auth-Username";
    static final String ROLES = "X-Auth-Roles";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String subject = request.getHeader(SUBJECT);
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authenticated caller");
        }
        String username = request.getHeader(USERNAME);
        String roles = request.getHeader(ROLES);
        Set<String> realmRoles = roles == null ? Set.of() : Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty() && !role.contains(":"))
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(subject, username == null ? subject : username, realmRoles);
    }
}
