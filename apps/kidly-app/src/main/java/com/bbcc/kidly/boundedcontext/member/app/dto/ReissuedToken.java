package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Member;

/**
 * 재발급 결과. 앱 계층 안에서만 쓴다. 엔티티를 담으므로 {@code in}으로 내보내지 않는다.
 *
 * @param member 로그인할 수 있는지 확인한 회원. Access Token을 발급할 때 다시 조회하지 않으려고 담는다
 * @param refreshToken 새 Refresh Token 원문. 쿠키로만 내려준다
 */
public record ReissuedToken(Member member, String refreshToken) {
}
