package com.bbcc.kidly.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@link PublicApi}가 붙은 컨트롤러 메서드의 경로 · HTTP 메서드와 일치하는 요청.
 * 시작할 때 한 번 모으므로 컨트롤러 매핑과 같은 경로만 공개된다.
 */
public class PublicApiRequestMatcher implements RequestMatcher {

    private final RequestMatcher delegate;

    public PublicApiRequestMatcher(RequestMappingHandlerMapping handlerMapping) {
        List<RequestMatcher> matchers = handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().hasMethodAnnotation(PublicApi.class))
            .flatMap(entry -> toMatchers(entry.getKey()))
            .toList();
        this.delegate = matchers.isEmpty() ? request -> false : new OrRequestMatcher(matchers);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return delegate.matches(request);
    }

    private static Stream<RequestMatcher> toMatchers(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        return mapping.getPatternValues().stream()
            .flatMap(pattern -> methods.isEmpty()
                ? Stream.of(PathPatternRequestMatcher.pathPattern(pattern))
                : methods.stream()
                    .map(method -> PathPatternRequestMatcher.pathPattern(method.asHttpMethod(), pattern)));
    }
}
