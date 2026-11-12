package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.childprofile.app.DevGuardianVerificationSeedService;
import com.bbcc.kidly.boundedcontext.childprofile.in.DevGuardianVerificationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevGuardianVerificationControllerProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(DevGuardianVerificationSeedService.class, () -> mock(DevGuardianVerificationSeedService.class))
        .withUserConfiguration(DevGuardianVerificationController.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "dev"})
    @DisplayName("local · test · dev 프로필에서는 더미 본인인증 API가 등록된다")
    void registeredInDevProfiles(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
            .run(context -> assertThat(context).hasSingleBean(DevGuardianVerificationController.class));
    }

    @Test
    @DisplayName("prod 프로필에서는 더미 본인인증 API가 등록되지 않는다")
    void notRegisteredInProd() {
        runner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(DevGuardianVerificationController.class));
    }
}
