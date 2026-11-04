package com.bbcc.kidly.boundedcontext.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ChildPassword(String value) {

    public ChildPassword {
        Objects.requireNonNull(value);
        if (value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("자녀 비밀번호는 8자 이상, UTF-8 72바이트 이하여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "ChildPassword[***]";
    }
}
