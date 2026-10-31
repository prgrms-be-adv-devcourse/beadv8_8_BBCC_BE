package com.bbcc.kidly.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 로그인한 회원 정보(ID · 닉네임 · 역할)를 컨트롤러 파라미터로 받는다. 예: {@code me(@LoginMember AuthMember member)}
 * 로그인이 필요한 API에서만 쓴다. 비로그인 요청은 principal이 AuthMember가 아니라서 500이 된다(null로 넘기지 않는다).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(errorOnInvalidType = true)
public @interface LoginMember {
}
