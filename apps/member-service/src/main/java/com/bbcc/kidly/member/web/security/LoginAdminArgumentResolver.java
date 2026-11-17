package com.bbcc.kidly.member.web.security;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

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
