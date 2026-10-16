package com.bbcc.kidly.boundedcontext.member.app.dto;

/**
 * 로그인 · 재발급 결과.
 *
 * @param refreshToken Refresh Token 원문. 쿠키로만 내려준다
 */
public record AuthTokens(String accessToken, String refreshToken) {
}
