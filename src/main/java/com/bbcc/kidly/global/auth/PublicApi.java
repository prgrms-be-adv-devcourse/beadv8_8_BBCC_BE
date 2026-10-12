package com.bbcc.kidly.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 없이 호출할 수 있는 API에 붙인다. 붙이지 않은 API는 모두 로그인이 필요하다.
 * 토큰을 보내면 검증한다. 유효하면 로그인 상태로, 잘못되거나 만료됐으면 401로 응답한다.
 * 비로그인 요청도 들어오므로 {@link LoginMember}는 함께 쓰지 않는다(비로그인 요청이 500이 된다).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
