package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.payment.events 리스너(#594): eventType 헤더로 고르고 본문(공통 칸 7개 + data)을 이벤트로 읽어 지금 결제 처리를 그대로 부른다.
 * 처리 실패를 주문 자체 보관(결제 완료 → payment_recovery, 결제 실패 → order_event_failure)에 남겼으면 처리 완료,
 * 동시 처리에서 진 쪽도 이긴 쪽이 이미 처리했으므로 처리 완료(다시 하면 환불 확인 WARN이 겹친다), 그 밖은 다시 던져 재시도 → DLT.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventKafkaListenerTest {

    private static final String EVENT_ID = "11111111-2222-3333-4444-555555555555";

    private final JsonMapper jsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Mock
    private PaymentEventListener paymentEventListener;

    private PaymentEventKafkaListener listener() {
        return new PaymentEventKafkaListener(jsonMapper, paymentEventListener);
    }

    @Test
    @DisplayName("payment.completed는 본문을 읽어 결제 완료 처리에 넘긴다")
    void completed() {
        listener().on(record("payment.completed", completedBody()));

        var captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentEventListener).onPaymentCompleted(captor.capture());
        assertThat(captor.getValue().data().orderId()).isEqualTo(7L);
        assertThat(captor.getValue().data().paymentId()).isEqualTo(500L);
        assertThat(captor.getValue().data().items()).singleElement()
            .satisfies(item -> assertThat(item.amount()).isEqualTo(25_000L));
    }

    @Test
    @DisplayName("payment.failed는 본문을 읽어 결제 실패 처리에 넘긴다")
    void failed() {
        listener().on(record("payment.failed", failedBody("INSUFFICIENT_BALANCE")));

        var captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentEventListener).onPaymentFailed(captor.capture());
        assertThat(captor.getValue().data().orderId()).isEqualTo(7L);
        assertThat(captor.getValue().data().reasonCode()).isEqualTo(PaymentFailureCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("주문이 받지 않는 종류(payment.refunded 등)는 건너뛴다")
    void skipsOtherEventType() {
        listener().on(record("payment.refunded", "읽을 수 없는 본문"));

        verifyNoInteractions(paymentEventListener);
    }

    @Test
    @DisplayName("모르는 실패 사유 값은 읽기 실패(JacksonException)로 던지고 처리를 부르지 않는다")
    void unknownEnumFails() {
        assertThatThrownBy(() -> listener().on(record("payment.failed", failedBody("NEW_REASON"))))
            .isInstanceOf(JacksonException.class);

        verifyNoInteractions(paymentEventListener);
    }

    @Test
    @DisplayName("처리 실패를 보관했으면 정상 반환한다(처리 완료)")
    void keptFailureCompletes() {
        var failure = new IllegalStateException("배송 생성 실패");
        EventFailureKeeper.markKept(failure);
        doThrow(failure).when(paymentEventListener).onPaymentCompleted(any());

        listener().on(record("payment.completed", completedBody()));

        verify(paymentEventListener).onPaymentCompleted(any());
    }

    @Test
    @DisplayName("동시 처리에서 진 쪽이면 정상 반환한다(이긴 쪽이 이미 처리, 다시 하면 환불 확인 WARN이 겹침)")
    void lostConcurrentDuplicateCompletes() {
        doThrow(new ObjectOptimisticLockingFailureException("Order", 7L))
            .when(paymentEventListener).onPaymentCompleted(any());

        listener().on(record("payment.completed", completedBody()));

        verify(paymentEventListener).onPaymentCompleted(any());
    }

    @Test
    @DisplayName("결제 실패 처리도 보관됐거나 진 쪽이면 정상 반환, 보관하지 못했으면 다시 던진다")
    void failedKeptOrRethrown() {
        var kept = new IllegalStateException("주문 UPDATE 실패");
        EventFailureKeeper.markKept(kept);
        doThrow(kept).when(paymentEventListener).onPaymentFailed(any());
        listener().on(record("payment.failed", failedBody("INSUFFICIENT_BALANCE")));

        var unkept = new IllegalStateException("보관도 실패");
        doThrow(unkept).when(paymentEventListener).onPaymentFailed(any());
        assertThatThrownBy(() -> listener().on(record("payment.failed", failedBody("INSUFFICIENT_BALANCE"))))
            .isSameAs(unkept);
    }

    @Test
    @DisplayName("보관하지 못한 처리 실패는 그대로 던진다(재시도 → DLT)")
    void unkeptFailureIsRethrown() {
        var failure = new IllegalStateException("보관 실패");
        doThrow(failure).when(paymentEventListener).onPaymentCompleted(any());

        assertThatThrownBy(() -> listener().on(record("payment.completed", completedBody()))).isSameAs(failure);
    }

    @Test
    @DisplayName("eventType 헤더가 없으면 읽기 실패(IllegalArgumentException)")
    void missingEventTypeFails() {
        var record = new ConsumerRecord<>("kidly.payment.events", 0, 0L, "7", "{}");

        assertThatThrownBy(() -> listener().on(record)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String completedBody() {
        return envelope("payment.completed", "{\"paymentId\":500,\"orderId\":7,\"memberId\":1,\"amount\":25000,"
            + "\"items\":[{\"orderItemId\":70,\"amount\":25000}],\"paidAt\":\"2026-10-30T09:00:00\"}");
    }

    private static String failedBody(String reasonCode) {
        return envelope("payment.failed", "{\"paymentId\":null,\"orderId\":7,\"memberId\":1,"
            + "\"requestedAmount\":25000,"
            + "\"reasonCode\":\"" + reasonCode + "\",\"failedAt\":\"2026-10-30T09:00:00\"}");
    }

    // 보내는 쪽(결제) 규칙의 본문: 공통 칸 7개 + data. 키 · correlationId = 주문 ID
    private static String envelope(String eventType, String data) {
        return "{\"eventId\":\"" + EVENT_ID + "\",\"eventType\":\"" + eventType + "\",\"schemaVersion\":1,"
            + "\"occurredAt\":\"2026-10-30T09:00:00\",\"producer\":\"payment\",\"aggregateId\":\"500\","
            + "\"correlationId\":\"7\",\"data\":" + data + "}";
    }

    private static ConsumerRecord<String, String> record(String eventType, String body) {
        var record = new ConsumerRecord<>("kidly.payment.events", 0, 0L, "7", body);
        record.headers().add("eventType", eventType.getBytes(UTF_8));
        record.headers().add("eventId", EVENT_ID.getBytes(UTF_8));
        return record;
    }
}
