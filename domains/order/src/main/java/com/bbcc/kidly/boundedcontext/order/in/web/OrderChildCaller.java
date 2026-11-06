package com.bbcc.kidly.boundedcontext.order.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 주문 API를 부른 로그인한 자녀를 자녀 프로필({@link OrderChild})로 받는다(#1000). 장바구니 {@code @CartCaller}(#998) ·
 * 조르기 {@code @WishCaller}(#906)와 같은 자녀 해석 규칙이고 자녀만 받는다: 자녀 헤더(X-Child-Account-Id · X-Child-Guardian-Id)
 * 둘이 있으면 계정 복사본으로 프로필을 찾는다. 회원 헤더(X-Member-Id)가 있으면 403, 헤더가 없거나 형식이 잘못되면 401, 자녀 계정
 * 연결을 확인할 수 없으면 403이다({@link OrderChildCallerArgumentResolver}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrderChildCaller {
}
