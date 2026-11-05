package com.bbcc.kidly.boundedcontext.auth.app.port;

/**
 * 주체 ID · 닉네임 · 역할로 Access Token을 발급한다.
 */
public interface AccessTokenIssuer {

    String issue(TokenSubject subject);
}
