package com.bbcc.kidly.member.web.security;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.member.support.security.AuthChild;
import com.bbcc.kidly.member.support.security.LoginChild;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginChildArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginChild.class);
    }

    @Override
    public AuthChild resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var accountId = id(webRequest, PrincipalHeaders.CHILD_ACCOUNT_ID);
        var guardianId = id(webRequest, PrincipalHeaders.CHILD_GUARDIAN_ID);
        return new AuthChild(accountId, guardianId);
    }

    private Long id(NativeWebRequest request, String header) {
        return PrincipalHeaders.parseId(request.getHeader(header)).filter(value -> value > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
