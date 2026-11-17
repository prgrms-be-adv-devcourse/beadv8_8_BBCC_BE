package com.bbcc.kidly.boundedcontext.auth.app.port;

public interface AccessTokenIssuer {

    String issue(TokenSubject subject);
}
