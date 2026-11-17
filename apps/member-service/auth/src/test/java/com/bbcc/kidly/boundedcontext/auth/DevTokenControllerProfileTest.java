package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.auth.app.DevTokenService;
import com.bbcc.kidly.boundedcontext.auth.in.DevTokenController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevTokenControllerProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(DevTokenService.class, () -> mock(DevTokenService.class))
        .withUserConfiguration(DevTokenController.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "dev"})
    @DisplayName("local · test · dev 프로필에서는 테스트 토큰 API가 등록된다")
    void registeredInDevelopmentProfiles(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
            .run(context -> assertThat(context).hasSingleBean(DevTokenController.class));
    }

    @Test
    @DisplayName("prod 프로필에서는 테스트 토큰 API가 등록되지 않는다")
    void notRegisteredInProd() {
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(DevTokenController.class));
    }
}
