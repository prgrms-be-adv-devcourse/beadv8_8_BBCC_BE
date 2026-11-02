package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecurityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("설정이 없으면 Secure 쿠키가 기본으로 켜진다")
    void defaultsToSecureCookie() {
        runner.run(context -> assertThat(context.getBean(SecurityProperties.class).secureCookie()).isTrue());
    }

    @Test
    @DisplayName("local 프로필처럼 꺼 두면 Secure 쿠키가 꺼진다")
    void bindsFalse() {
        runner.withPropertyValues("kidly.security.secure-cookie=false")
            .run(context -> assertThat(context.getBean(SecurityProperties.class).secureCookie()).isFalse());
    }

    @EnableConfigurationProperties(SecurityProperties.class)
    static class PropertiesConfig {
    }
}
