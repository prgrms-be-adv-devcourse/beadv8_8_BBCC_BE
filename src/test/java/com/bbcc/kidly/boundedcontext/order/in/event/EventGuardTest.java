package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EventGuardTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 9, 0);
    private static final StockDeductedEvent EVENT =
        new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT, new StockDeductedEvent.Data(7L));

    @Test
    @DisplayName("빠진 값이 없으면(null) true이고 로그를 남기지 않는다")
    void acceptsWithoutLog(CapturedOutput output) {
        assertThat(EventGuard.accept("재고", EVENT, null)).isTrue();
        assertThat(EventGuard.accept("판매자", EVENT, "sellerId", 2L, null)).isTrue();

        assertThat(output).doesNotContain("이벤트 무시");
    }

    @Test
    @DisplayName("빠진 값이 있으면 false이고 WARN 한 줄(이벤트 이름 · reason)을 남긴다")
    void rejectsWithWarn(CapturedOutput output) {
        boolean accepted = EventGuard.accept("재고", EVENT, "missing-order-id");

        assertThat(accepted).isFalse();
        assertThat(output).containsPattern("WARN.*재고 이벤트 무시: event=stock.deducted reason=missing-order-id");
        assertThat(skipLines(output)).isEqualTo(1);
    }

    @Test
    @DisplayName("ID를 함께 넘기면 같은 줄에 ID 이름=값을 넣는다(값이 없으면 null)")
    void rejectsWithIdOnSameLine(CapturedOutput output) {
        assertThat(EventGuard.accept("판매자", EVENT, "sellerId", 2L, "missing-status")).isFalse();
        assertThat(EventGuard.accept("판매자", EVENT, "sellerId", null, "missing-data")).isFalse();

        assertThat(output).containsPattern(
            "WARN.*판매자 이벤트 무시: event=stock.deducted sellerId=2 reason=missing-status");
        assertThat(output).containsPattern(
            "WARN.*판매자 이벤트 무시: event=stock.deducted sellerId=null reason=missing-data");
        assertThat(skipLines(output)).isEqualTo(2);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("receivedEvents")
    @DisplayName("주문이 받는 이벤트의 eventType()은 지금까지 로그에 쓰던 이름과 같다(이름 상수로 바꿔도 로그 값 그대로)")
    void eventTypeMatchesLoggedName(DomainEvent event, String loggedName) {
        assertThat(event.eventType()).isEqualTo(loggedName);
    }

    static Stream<Arguments> receivedEvents() {
        return Stream.of(
            Arguments.of(new PaymentCompletedEvent(UUID.randomUUID(), OCCURRED_AT, null), "payment.completed"),
            Arguments.of(new PaymentFailedEvent(UUID.randomUUID(), OCCURRED_AT, null), "payment.failed"),
            Arguments.of(new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT, null), "stock.deducted"),
            Arguments.of(new StockDeductionFailedEvent(UUID.randomUUID(), OCCURRED_AT, null),
                "stock.deduction-failed"),
            Arguments.of(new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, null), "product.changed"),
            Arguments.of(new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, null), "child.changed"),
            Arguments.of(new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT, null), "seller.changed"));
    }

    private static long skipLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains("이벤트 무시")).count();
    }
}
