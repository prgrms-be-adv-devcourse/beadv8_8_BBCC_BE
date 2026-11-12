package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.member.app.DevMemberSeedService;
import com.bbcc.kidly.boundedcontext.member.in.DevMemberController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevMemberControllerProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(DevMemberSeedService.class, () -> mock(DevMemberSeedService.class))
        .withUserConfiguration(DevMemberController.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "dev"})
    @DisplayName("local · test · dev 프로필에서는 더미 회원 API가 등록된다")
    void registeredInDevProfiles(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
            .run(context -> assertThat(context).hasSingleBean(DevMemberController.class));
    }

    @Test
    @DisplayName("prod 프로필에서는 더미 회원 API가 등록되지 않는다")
    void notRegisteredInProd() {
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(DevMemberController.class));
    }
}
