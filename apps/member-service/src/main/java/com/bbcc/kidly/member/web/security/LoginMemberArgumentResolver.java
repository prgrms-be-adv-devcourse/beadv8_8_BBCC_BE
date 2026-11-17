package com.bbcc.kidly.member.web.security;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.member.support.security.AuthMember;
import com.bbcc.kidly.member.support.security.LoginMember;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class);
    }

    @Override
    public AuthMember resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var memberId = MemberHeaders.parseMemberId(webRequest.getHeader(MemberHeaders.MEMBER_ID))
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        try {
            return new AuthMember(memberId, nickname(webRequest));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private static String nickname(NativeWebRequest webRequest) {
        return Optional.ofNullable(webRequest.getHeader(MemberHeaders.MEMBER_NICKNAME))
            .map(MemberHeaders::decodeNickname)
            .orElse(null);
    }
}
