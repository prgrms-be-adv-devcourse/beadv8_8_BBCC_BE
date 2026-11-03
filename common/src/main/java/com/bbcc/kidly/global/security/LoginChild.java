package com.bbcc.kidly.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 자녀를 컨트롤러 파라미터로 받는다. 예: {@code wishes(@LoginChild AuthChild child)}
 * 자녀 전용 API(/api/v1/child/**)에서 쓴다. 경로의 접근 검사는 Gateway가 한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginChild {
}
