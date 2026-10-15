package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.AdminProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AdminPropertiesTest {

    @Test
    @DisplayName("쉼표로 나눈 이메일 목록을 받고, 앞뒤 공백 · 대소문자를 무시하고 비교한다")
    void bindsCommaSeparatedEmails() {
        var properties = bind(Map.of("admin.emails", "admin@example.com, Owner@Example.com"));

        assertThat(properties.isAdmin("admin@example.com")).isTrue();
        assertThat(properties.isAdmin("owner@example.com")).isTrue();
        assertThat(properties.isAdmin("ADMIN@EXAMPLE.COM")).isTrue();
        assertThat(properties.isAdmin("user@example.com")).isFalse();
    }

    @Test
    @DisplayName("설정이 비어 있거나 없으면 아무도 관리자가 아니다")
    void emptyMeansNoAdmin() {
        assertThat(bind(Map.of("admin.emails", "")).isAdmin("admin@example.com")).isFalse();
        assertThat(bind(Map.of()).isAdmin("admin@example.com")).isFalse();
    }

    @Test
    @DisplayName("이메일이 없는 회원은 관리자가 아니다")
    void nullEmailIsNotAdmin() {
        var properties = bind(Map.of("admin.emails", "admin@example.com"));

        assertThat(properties.isAdmin(null)).isFalse();
    }

    private static AdminProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
            .bindOrCreate("admin", AdminProperties.class);
    }
}
