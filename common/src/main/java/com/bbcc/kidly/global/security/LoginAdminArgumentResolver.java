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
 * {@link LoginAdmin} 파라미터를 Gateway가 관리자 토큰에서 붙인 X-Admin-Id 헤더로 만든다.
 * 없거나 잘못되면 401이다. X-Member-*는 보지 않는다(회원 토큰에는 X-Admin-Id가 없다).
 */
public class LoginAdminArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginAdmin.class);
    }

    @Override
    public AuthAdmin resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var id = PrincipalHeaders.parseId(webRequest.getHeader(PrincipalHeaders.ADMIN_ID))
            .filter(value -> value > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return new AuthAdmin(id);
    }
}
