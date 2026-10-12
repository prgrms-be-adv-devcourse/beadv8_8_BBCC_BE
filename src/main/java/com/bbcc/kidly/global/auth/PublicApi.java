package com.bbcc.kidly.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 없이 호출할 수 있는 API에 붙인다. 붙이지 않은 API는 모두 로그인이 필요하다.
 * 이 API의 요청은 Bearer 토큰을 검증하지 않으므로 {@link LoginMember}를 함께 쓸 수 없다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
