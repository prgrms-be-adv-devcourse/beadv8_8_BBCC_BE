package com.bbcc.kidly.boundedcontext.member.app.dto;

/**
 * 소셜 로그인 결과. 컨트롤러는 이 회원 ID로 토큰을 발급한다.
 */
public record LoggedInMember(Long memberId) {
}
