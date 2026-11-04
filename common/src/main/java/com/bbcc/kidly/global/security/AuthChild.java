package com.bbcc.kidly.global.security;

/**
 * 로그인 자녀. 컨트롤러에서 {@code @LoginChild AuthChild child}로 받는다({@link LoginChildArgumentResolver}).
 * accountId는 자녀 계정 ID(회원 · 자녀 프로필과 ID 공간이 다르다), guardianId는 보호자 회원 ID다.
 */
public record AuthChild(Long accountId, Long guardianId) {
}
