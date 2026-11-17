package com.bbcc.kidly.boundedcontext.auth.app.dto;

import com.bbcc.kidly.boundedcontext.auth.app.port.TokenSubject;

public record ReissuedToken(TokenSubject subject, String refreshToken) {
}
