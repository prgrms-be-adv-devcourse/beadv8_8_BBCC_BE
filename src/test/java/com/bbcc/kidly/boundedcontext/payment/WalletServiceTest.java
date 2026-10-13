package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.payment.app.WalletService;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("회원의 지갑 잔액을 준다")
    void getBalance() {
        given(walletRepository.findByMemberId(PaymentFixture.MEMBER_ID))
            .willReturn(Optional.of(PaymentFixture.wallet(15_000L)));

        var result = walletService.getBalance(PaymentFixture.MEMBER_ID);

        assertThat(result.balance()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("지갑이 없으면 WALLET_NOT_FOUND")
    void walletNotFound() {
        given(walletRepository.findByMemberId(PaymentFixture.MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(PaymentFixture.MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.WALLET_NOT_FOUND));
    }
}
