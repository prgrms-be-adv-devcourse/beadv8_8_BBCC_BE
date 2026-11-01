package com.bbcc.kidly.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 회원 정보(ID · 닉네임 · 역할)를 컨트롤러 파라미터로 받는다. 예: {@code me(@LoginMember AuthMember member)}
 * 로그인이 필요한 API에서만 쓴다. 이 파라미터가 있는 API는 회원 헤더가 없거나 잘못되면 401이다({@link LoginMemberArgumentResolver}).
 * 붙이지 않은 API는 로그인 없이 호출할 수 있다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMember {
}
