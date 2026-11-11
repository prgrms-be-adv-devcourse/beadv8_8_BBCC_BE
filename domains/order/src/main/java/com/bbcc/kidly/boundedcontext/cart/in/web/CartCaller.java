package com.bbcc.kidly.boundedcontext.cart.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 장바구니 API를 부른 사람(회원 또는 로그인한 자녀, #998)을 장바구니 주인({@code CartActors.Actor})으로 받는다.
 * 규칙은 조르기의 {@code @WishCaller}(#906)와 같다: 회원 헤더(X-Member-Id)가 있으면 회원, 없고 자녀 헤더(X-Child-Account-Id ·
 * X-Child-Guardian-Id)가 둘 다 있으면 자녀다. 둘 다 없거나 형식이 잘못되면 401, 자녀 계정 연결을 확인할 수 없으면 403이다
 * ({@link CartCallerArgumentResolver}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CartCaller {
}
