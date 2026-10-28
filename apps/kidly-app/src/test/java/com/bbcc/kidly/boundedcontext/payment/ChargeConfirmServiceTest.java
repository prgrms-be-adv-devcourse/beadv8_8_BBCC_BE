package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.CHARGE_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.TOSS_ORDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeConfirmService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmTarget;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeConfirmServiceTest {

    private static final ConfirmChargeRequest REQUEST = new ConfirmChargeRequest("payment-key", TOSS_ORDER_ID, 10_000L);

    @Mock
    private ChargeService chargeService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private ChargeConfirmService chargeConfirmService;

    @BeforeEach
    void startConfirming() {
        given(chargeService.startConfirming(MEMBER_ID, REQUEST))
            .willReturn(new ChargeConfirmTarget(CHARGE_ID, TOSS_ORDER_ID, 10_000L));
    }

    @Test
    @DisplayName("토스가 승인하면 입금 결과를 돌려준다. 멱등 키는 충전 주문번호다")
    void approved() {
        PgApproval approval = new PgApproval("payment-key", 10_000L, LocalDateTime.now());
        given(paymentGateway.confirm("payment-key", TOSS_ORDER_ID, 10_000L, TOSS_ORDER_ID))
            .willReturn(new PgResult.Success<>(approval));
        ChargeConfirmResult done = ChargeConfirmResult.done(CHARGE_ID, 10_000L, 10_000L);
        given(chargeService.approve(CHARGE_ID, approval)).willReturn(done);

        assertThat(chargeConfirmService.confirm(MEMBER_ID, REQUEST)).isEqualTo(done);
    }

    @Test
    @DisplayName("토스가 거절하면 실패로 저장하고 토스 메시지로 CHARGE_REJECTED를 던진다")
    void rejected() {
        given(paymentGateway.confirm(anyString(), anyString(), any(Long.class), anyString()))
            .willReturn(new PgResult.Rejected<>("REJECT_CARD_COMPANY", "카드사에서 거절했습니다"));

        assertThatThrownBy(() -> chargeConfirmService.confirm(MEMBER_ID, REQUEST))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(PaymentErrorCode.CHARGE_REJECTED);
                assertThat(e.getDetail()).isEqualTo("카드사에서 거절했습니다");
            });
        then(chargeService).should().fail(CHARGE_ID, "REJECT_CARD_COMPANY");
        then(chargeService).should(never()).approve(any(), any());
    }

    @Test
    @DisplayName("결과를 모르면 실패로 처리하지 않고 승인 중으로 돌려준다")
    void unknown() {
        given(paymentGateway.confirm(anyString(), anyString(), any(Long.class), anyString()))
            .willReturn(new PgResult.Unknown<>("timeout"));

        ChargeConfirmResult result = chargeConfirmService.confirm(MEMBER_ID, REQUEST);

        assertThat(result).isEqualTo(new ChargeConfirmResult(CHARGE_ID, "CONFIRMING", 10_000L, null));
        then(chargeService).should(never()).fail(any(), any());
        then(chargeService).should(never()).approve(any(), any());
    }
}
