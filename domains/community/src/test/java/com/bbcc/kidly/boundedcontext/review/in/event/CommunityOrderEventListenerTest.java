package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ReviewEligibilityRecorder.PurchaseConfirmed;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CommunityOrderEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusMinutes(1);
    private static final LocalDateTime CONFIRMED_AT = OCCURRED_AT.minusSeconds(1);

    @Mock
    private ReviewEligibilityRecorder recorder;

    @InjectMocks
    private CommunityOrderEventListener listener;

    @Captor
    private ArgumentCaptor<PurchaseConfirmed> command;

    @Test
    @DisplayName("구매확정을 자격 입력으로 바꿔 넘긴다: 주문상품 · 주문 · 회원 · 상품 · 옵션 · 사이즈 · 구매확정 시각")
    void convertsEvent() {
        listener.onPurchaseConfirmed(event(data(11L, 13L, 14L, CONFIRMED_AT)));

        verify(recorder).record(command.capture());
        assertThat(command.getValue()).isEqualTo(new PurchaseConfirmed(11L, 12L, 13L, 14L, 15L, "110", CONFIRMED_AT));
    }

    @Test
    @DisplayName("구매확정 시각이 없으면 이벤트 발생 시각을 쓴다")
    void confirmedAtFallsBackToOccurredAt() {
        listener.onPurchaseConfirmed(event(data(11L, 13L, 14L, null)));

        verify(recorder).record(command.capture());
        assertThat(command.getValue().confirmedAt()).isEqualTo(OCCURRED_AT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingValues")
    @DisplayName("자격을 만들 수 없는 값이 없으면 WARN 한 줄을 남기고 넘기지 않는다")
    void ignoresMissingRequiredValue(String reason, OrderItemPurchaseConfirmedEvent event, CapturedOutput output) {
        listener.onPurchaseConfirmed(event);

        verifyNoInteractions(recorder);
        assertThat(output.getOut()).contains("구매확정 이벤트 무시", "event=order-item.purchase-confirmed",
            "reason=" + reason);
    }

    private static Stream<Arguments> missingValues() {
        return Stream.of(
            Arguments.of("missing-data", event(null)),
            Arguments.of("missing-order-item-id", event(data(null, 13L, 14L, CONFIRMED_AT))),
            Arguments.of("missing-member-id", event(data(11L, null, 14L, CONFIRMED_AT))),
            Arguments.of("missing-product-id", event(data(11L, 13L, null, CONFIRMED_AT))),
            Arguments.of("missing-occurred-at", new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), null,
                data(11L, 13L, 14L, CONFIRMED_AT))));
    }

    private static OrderItemPurchaseConfirmedEvent event(OrderItemPurchaseConfirmedEvent.Data data) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), OCCURRED_AT, data);
    }

    private static OrderItemPurchaseConfirmedEvent.Data data(Long orderItemId, Long memberId, Long productId,
        LocalDateTime confirmedAt) {
        return new OrderItemPurchaseConfirmedEvent.Data(orderItemId, 12L, memberId, 20L, null, productId, 15L, "110", 1,
            25_000L, confirmedAt);
    }
}
