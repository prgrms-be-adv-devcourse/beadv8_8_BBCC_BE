package com.bbcc.kidly.global.security;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginMember} 파라미터를 API Gateway가 토큰을 검증하고 붙인 X-Member-* 헤더({@link MemberHeaders})로 만든다.
 * 서비스가 Gateway 뒤에서만 열린다는 전제다(Gateway가 밖에서 온 X-Member-* 헤더를 지운다).
 * 회원 ID 헤더가 없거나 헤더 값이 잘못되면 401이다. 역할 헤더는 보지 않는다(역할은 Gateway가 검사한다).
 */
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
            // 밖에서 온 헤더 값은 예외 메시지에 넣지 않는다
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private static String nickname(NativeWebRequest webRequest) {
        return Optional.ofNullable(webRequest.getHeader(MemberHeaders.MEMBER_NICKNAME))
            .map(MemberHeaders::decodeNickname)
            .orElse(null);
    }
}
