package com.bbcc.kidly.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 관리자를 컨트롤러 파라미터로 받는다. 예: {@code suspend(@LoginAdmin AuthAdmin admin, ...)}
 * 관리자 ID가 필요한 API에서만 쓴다. 관리자 경로의 접근 검사는 Gateway가 한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginAdmin {
}
