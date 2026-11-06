package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishErrorCode;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link WishCaller} 파라미터를 Gateway가 붙인 주체 헤더로 만든다(#906). 공용 해석기(@LoginMember · @LoginChild)는 그대로 두고
 * 조르기 API에서만 두 주체를 함께 받는다. 헤더 값 규칙(0-9만 · Long 범위 · 0보다 큼)은 공용 해석기와 같다.
 * <ul>
 *   <li>회원 헤더가 있으면 부모: 값이 잘못되면 401</li>
 *   <li>회원 헤더가 없고 자녀 헤더 둘이 모두 맞으면 자녀: 계정 복사본으로 프로필을 찾는다(없음 · 삭제 · 보호자 다름 403)</li>
 *   <li>그 밖(헤더 없음 · 자녀 헤더 하나만 · 형식 오류)은 401</li>
 * </ul>
 */
@RequiredArgsConstructor
public class WishCallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final WishActors wishActors;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(WishCaller.class)
            && WishActor.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public WishActor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String memberId = webRequest.getHeader(MemberHeaders.MEMBER_ID);
        if (memberId != null) {
            return wishActors.parent(parseId(memberId));
        }
        return wishActors.child(parseId(webRequest.getHeader(PrincipalHeaders.CHILD_ACCOUNT_ID)),
                parseId(webRequest.getHeader(PrincipalHeaders.CHILD_GUARDIAN_ID)))
            .orElseThrow(() -> new BusinessException(WishErrorCode.CHILD_ACCOUNT_FORBIDDEN));
    }

    private static Long parseId(String value) {
        return PrincipalHeaders.parseId(value)
            .filter(id -> id > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
