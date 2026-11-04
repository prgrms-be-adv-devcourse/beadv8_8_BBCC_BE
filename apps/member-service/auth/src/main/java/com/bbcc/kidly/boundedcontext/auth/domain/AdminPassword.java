package com.bbcc.kidly.boundedcontext.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 관리자 비밀번호 원문(값 객체). 12자 이상, UTF-8 72바이트 이하(BCrypt가 보는 최대 길이)만 받는다.
 */
public record AdminPassword(String value) {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    public AdminPassword {
        Objects.requireNonNull(value);
        if (value.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("관리자 비밀번호는 " + MIN_LENGTH + "자 이상이어야 합니다");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("관리자 비밀번호는 UTF-8 " + MAX_BYTES + "바이트 이하여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "AdminPassword[***]";
    }
}
