package com.bbcc.kidly.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * 요청을 처리할 컨트롤러 메서드에 {@link PublicApi}가 붙어 있으면 일치한다.
 * 경로 패턴이 아니라 MVC가 실제로 고르는 핸들러로 판단하므로,
 * 공개 API 패턴과 겹치는 다른 API(예: /x/{id}와 /x/mine)가 공개되지 않는다.
 * 핸들러를 찾지 못하면 공개가 아니다(로그인 필요).
 */
@Component
public class PublicApiRequestMatcher implements RequestMatcher {

    private final RequestMappingHandlerMapping handlerMapping;

    public PublicApiRequestMatcher(
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        HandlerMethod handler = findHandler(request);
        return handler != null && handler.hasMethodAnnotation(PublicApi.class);
    }

    // 시큐리티 필터는 DispatcherServlet보다 먼저 실행되므로 요청 경로를 직접 파싱해 두고, 끝나면 되돌린다
    private HandlerMethod findHandler(HttpServletRequest request) {
        boolean parsed = ServletRequestPathUtils.hasParsedRequestPath(request);
        try {
            if (!parsed) {
                ServletRequestPathUtils.parseAndCache(request);
            }
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            return chain != null && chain.getHandler() instanceof HandlerMethod handler ? handler : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (!parsed) {
                ServletRequestPathUtils.clearParsedRequestPath(request);
            }
        }
    }
}
