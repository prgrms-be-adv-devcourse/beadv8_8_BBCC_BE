package com.bbcc.kidly.boundedcontext.notification.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브라우저의 FID(Firebase Installation ID). 프론트가 Firebase Messaging의 register · onRegistered로 받은 값이다.
 */
public record PushTokenRequest(@NotBlank @Size(max = 1024) String fid) {

    // FID는 브라우저 식별값이라 검증 실패 · 로그에 찍히지 않게 숨긴다
    @Override
    public String toString() {
        return "PushTokenRequest[fid=***]";
    }
}
