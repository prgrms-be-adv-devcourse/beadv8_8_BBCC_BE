package com.bbcc.kidly.global.security;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginChild} 파라미터를 Gateway가 자녀 토큰에서 붙인 X-Child-Account-Id · X-Child-Guardian-Id 헤더로 만든다.
 * 둘 중 하나라도 없거나 잘못되면 401이다. X-Member-*는 보지 않는다(자녀 토큰에는 X-Member-Id가 없다).
 */
public class LoginChildArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginChild.class);
    }

    @Override
    public AuthChild resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var accountId = parseId(webRequest.getHeader(PrincipalHeaders.CHILD_ACCOUNT_ID));
        var guardianId = parseId(webRequest.getHeader(PrincipalHeaders.CHILD_GUARDIAN_ID));
        return new AuthChild(accountId, guardianId);
    }

    private static Long parseId(String value) {
        return PrincipalHeaders.parseId(value)
            .filter(id -> id > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
