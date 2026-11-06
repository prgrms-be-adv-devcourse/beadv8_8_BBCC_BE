package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.payment.app.DevChargeService;
import com.bbcc.kidly.boundedcontext.payment.domain.DevChargeRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletLedgerRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.in.DevWalletController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevWalletControllerProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(WalletRepository.class, () -> mock(WalletRepository.class))
        .withBean(WalletLedgerRepository.class, () -> mock(WalletLedgerRepository.class))
        .withBean(DevChargeRepository.class, () -> mock(DevChargeRepository.class))
        .withUserConfiguration(DevChargeService.class, DevWalletController.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "dev"})
    @DisplayName("local · test · dev 프로필에서는 개발용 예치금 충전 API가 등록된다")
    void registeredOutsideProd(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
            .run(context -> assertThat(context)
                .hasSingleBean(DevWalletController.class)
                .hasSingleBean(DevChargeService.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "default"})
    @DisplayName("prod 프로필이나 프로필이 없으면 개발용 예치금 충전 API가 등록되지 않는다")
    void notRegisteredInProd(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
            .run(context -> assertThat(context)
                .doesNotHaveBean(DevWalletController.class)
                .doesNotHaveBean(DevChargeService.class));
    }
}
