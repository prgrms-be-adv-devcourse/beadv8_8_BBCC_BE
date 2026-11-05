package com.bbcc.kidly.boundedcontext.wish.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 조르기 API를 부른 사람(부모 회원 또는 로그인한 자녀, #906)을 {@code WishActor} 파라미터로 받는다.
 * 회원 헤더(X-Member-Id)가 있으면 부모, 없고 자녀 헤더(X-Child-Account-Id · X-Child-Guardian-Id)가 둘 다 있으면 자녀다.
 * 둘 다 없거나 형식이 잘못되면 401({@code @LoginMember}와 같은 응답), 자녀 계정 연결을 확인할 수 없으면 403이다
 * ({@link WishCallerArgumentResolver}). 조르기 API에서만 쓴다(다른 주문 API는 {@code @LoginMember} 그대로).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface WishCaller {
}
