package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.global.config.PropertiesConfig;
import com.bbcc.kidly.global.security.AuthProperties.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * kidly.auth.mode는 앱과 같은 방식(PropertiesConfig의 스캔)으로 등록해 바인딩한다.
 * 체인을 고르는 @ConditionalOnProperty(SecurityConfig)도 대소문자를 가리지 않으므로 두 쪽이 같은 값을 받는다.
 */
class AuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("값이 없으면 jwt 모드다")
    void defaultsToJwt() {
        runner.run(context -> assertThat(context.getBean(AuthProperties.class).mode()).isEqualTo(Mode.JWT));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "jwt, JWT",
        "gateway, GATEWAY",
        "GATEWAY, GATEWAY"
    })
    @DisplayName("jwt · gateway를 대소문자 구분 없이 받는다")
    void bindsMode(String value, Mode expected) {
        runner.withPropertyValues("kidly.auth.mode=" + value)
            .run(context -> assertThat(context.getBean(AuthProperties.class).mode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("모르는 값(오타)이면 기동에 실패한다. 두 체인이 다 빠지고 기본 체인으로 뜨지 않게 한다")
    void failsOnUnknownMode() {
        runner.withPropertyValues("kidly.auth.mode=gatway")
            .run(context -> assertThat(context).hasFailed());
    }
}
