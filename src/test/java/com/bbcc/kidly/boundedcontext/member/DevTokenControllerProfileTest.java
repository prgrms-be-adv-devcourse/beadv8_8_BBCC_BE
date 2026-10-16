package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.member.app.DevTokenService;
import com.bbcc.kidly.boundedcontext.member.in.DevTokenController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevTokenControllerProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(DevTokenService.class, () -> mock(DevTokenService.class))
        .withUserConfiguration(DevTokenController.class);

    @Test
    @DisplayName("local 프로필에서는 테스트 토큰 API가 등록된다")
    void registeredInLocal() {
        runner.withPropertyValues("spring.profiles.active=local")
            .run(context -> assertThat(context).hasSingleBean(DevTokenController.class));
    }

    @Test
    @DisplayName("test 프로필에서는 테스트 토큰 API가 등록된다")
    void registeredInTest() {
        runner.withPropertyValues("spring.profiles.active=test")
            .run(context -> assertThat(context).hasSingleBean(DevTokenController.class));
    }

    @Test
    @DisplayName("dev 서버(prod,dev 프로필)에서는 테스트 토큰 API가 등록된다")
    void registeredInDev() {
        runner.withPropertyValues("spring.profiles.active=prod,dev")
            .run(context -> assertThat(context).hasSingleBean(DevTokenController.class));
    }

    @Test
    @DisplayName("prod 프로필에서는 테스트 토큰 API가 등록되지 않는다")
    void notRegisteredInProd() {
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(DevTokenController.class));
    }
}
