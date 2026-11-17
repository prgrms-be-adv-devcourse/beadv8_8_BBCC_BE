package com.bbcc.kidly.boundedcontext.favorite.in.web;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteCallerService;
import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteActor;
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
 * {@link FavoriteCaller} 파라미터를 Gateway가 붙인 주체 헤더로 만든다.
 * <ol>
 *   <li>X-Member-Id가 있으면 회원 본인(값이 잘못되면 401)</li>
 *   <li>없으면 X-Child-Account-Id · X-Child-Guardian-Id 둘 다로 자녀 계정(하나라도 없거나 잘못되면 401)</li>
 *   <li>지운 자녀 계정이면 403 FAVORITE_CHILD_ACCOUNT_DELETED</li>
 * </ol>
 */
@RequiredArgsConstructor
public class FavoriteCallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final FavoriteCallerService callerService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(FavoriteCaller.class)
            && FavoriteActor.class.equals(parameter.getParameterType());
    }

    @Override
    public FavoriteActor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String memberId = webRequest.getHeader(MemberHeaders.MEMBER_ID);
        if (memberId != null) {
            return callerService.checkActive(FavoriteActor.member(parseId(memberId)));
        }
        Long accountId = parseId(webRequest.getHeader(PrincipalHeaders.CHILD_ACCOUNT_ID));
        Long guardianId = parseId(webRequest.getHeader(PrincipalHeaders.CHILD_GUARDIAN_ID));
        return callerService.checkActive(FavoriteActor.child(accountId, guardianId));
    }

    private static Long parseId(String value) {
        return PrincipalHeaders.parseId(value)
            .filter(id -> id > 0)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
