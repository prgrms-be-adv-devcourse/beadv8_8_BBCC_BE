package com.bbcc.kidly.member.support.security;

/**
 * 로그인 관리자. 컨트롤러에서 {@code @LoginAdmin AuthAdmin admin}으로 받는다({@link LoginAdminArgumentResolver}).
 * 관리자는 회원과 ID 공간이 다르다. 관리자는 한 종류라 역할이 없다.
 */
public record AuthAdmin(Long id) {
}
