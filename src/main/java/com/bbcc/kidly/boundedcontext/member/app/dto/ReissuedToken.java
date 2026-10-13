package com.bbcc.kidly.boundedcontext.member.app.dto;

/**
 * 재발급 결과.
 *
 * @param refreshToken 새 Refresh Token 원문. 쿠키로만 내려준다
 */
public record ReissuedToken(Long memberId, String refreshToken) {
}
