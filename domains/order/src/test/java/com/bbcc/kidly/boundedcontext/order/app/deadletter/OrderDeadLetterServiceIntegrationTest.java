package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DLT로 온 메시지를 order_dead_letter에 저장한다(#601). 같은 원래 위치(토픽 · 파티션 · 오프셋)는 1건이다.
 */
@IntegrationTest
class OrderDeadLetterServiceIntegrationTest {

    private static final String PAYMENT_BODY = """
        {"eventId":"e-1","eventType":"payment.completed","schemaVersion":1,"occurredAt":"2026-10-31T09:30:00",
         "producer":"payment","aggregateId":"500","correlationId":"42","data":{"paymentId":500,"orderId":42}}""";

    @Autowired
    private OrderDeadLetterService deadLetterService;

    @Autowired
    private OrderDeadLetterRepository deadLetterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("처음 온 메시지는 OPEN 1건: 무슨 메시지 · 왜 실패 · 어느 주문 · 언제 칸이 채워지고 처리 칸은 비어 있다")
    void savesFirstReceipt() {
        deadLetterService.receive(command(7L, "org.springframework.orm.ObjectOptimisticLockingFailureException",
            PAYMENT_BODY));

        OrderDeadLetter letter = single();
        assertThat(letter.getSourceTopic()).isEqualTo("kidly.payment.events");
        assertThat(letter.getSourcePartition()).isEqualTo(1);
        assertThat(letter.getSourceOffset()).isEqualTo(7L);
        assertThat(letter.getMessageKey()).isEqualTo("42");
        assertThat(letter.getEventType()).isEqualTo("payment.completed");
        assertThat(letter.getEventId()).isEqualTo("e-1");
        assertThat(letter.getPayload()).isEqualTo(PAYMENT_BODY);
        assertThat(letter.getProducer()).isEqualTo("payment");
        assertThat(letter.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 9, 30));
        assertThat(letter.getAggregateId()).isEqualTo("500");
        assertThat(letter.getCorrelationId()).isEqualTo("42");
        assertThat(letter.getOrderId()).isEqualTo(42L);
        assertThat(letter.getFailureType()).isEqualTo(OrderDeadLetter.FailureType.STORE_FAILED);
        assertThat(letter.getExceptionClass())
            .isEqualTo("org.springframework.orm.ObjectOptimisticLockingFailureException");
        assertThat(letter.getExceptionMessage()).isEqualTo("실패 메시지");
        assertThat(letter.getStackTraceHead()).isEqualTo("trace");
        assertThat(letter.getFirstReceivedAt()).isNotNull().isEqualTo(letter.getLastReceivedAt());
        assertThat(letter.getReceivedCount()).isEqualTo(1);
        assertThat(letter.getStatus()).isEqualTo(OrderDeadLetter.Status.OPEN);
        assertThat(letter.getHandledBy()).isNull();
        assertThat(letter.getHandledAt()).isNull();
        assertThat(letter.getMemo()).isNull();
        assertThat(letter.getReprocessCount()).isZero();
        assertThat(letter.getLastReprocessedAt()).isNull();
        assertThat(letter.getLastReprocessResult()).isNull();
    }

    @Test
    @DisplayName("같은 원래 위치의 메시지가 다시 오면 1건 그대로, 받은 횟수 · 마지막 받은 시각만 바뀐다(처음 값 · 상태 유지)")
    void sameSourceIsOneRow() {
        deadLetterService.receive(command(7L, "java.lang.IllegalStateException", PAYMENT_BODY));
        OrderDeadLetter first = single();
        jdbcTemplate.update("update orders.order_dead_letter set status = 'CLOSED', memo = '확인함'");

        deadLetterService.receive(command(7L, "tools.jackson.databind.exc.InvalidFormatException", "{}"));

        OrderDeadLetter again = single();
        assertThat(again.getReceivedCount()).isEqualTo(2);
        assertThat(again.getLastReceivedAt()).isAfter(first.getLastReceivedAt());
        assertThat(again.getFirstReceivedAt()).isEqualTo(first.getFirstReceivedAt());
        assertThat(again.getFailureType()).isEqualTo(OrderDeadLetter.FailureType.PROCESS_FAILED);
        assertThat(again.getExceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(again.getPayload()).isEqualTo(PAYMENT_BODY);
        assertThat(again.getStatus()).isEqualTo(OrderDeadLetter.Status.CLOSED);
        assertThat(again.getMemo()).isEqualTo("확인함");
    }

    @Test
    @DisplayName("원래 위치가 다르면(오프셋) 따로 저장한다")
    void differentSourceIsAnotherRow() {
        deadLetterService.receive(command(7L, "java.lang.IllegalStateException", PAYMENT_BODY));
        deadLetterService.receive(command(8L, "java.lang.IllegalStateException", PAYMENT_BODY));

        assertThat(jdbcTemplate.queryForList("select source_offset from orders.order_dead_letter order by id",
            Long.class)).containsExactly(7L, 8L);
    }

    @Test
    @DisplayName("본문을 못 읽은 메시지(eventType 헤더 없음 · 깨진 본문)는 공통 칸 · 관련 주문 ID 빈칸으로 저장한다")
    void unreadableMessageHasEmptyFields() {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.product.events", 0, 3L, null, null, null,
            "{broken", "com.bbcc.kidly.boundedcontext.order.app.deadletter.UnreadableOrderMessageException",
            "eventType 헤더 없음", null));

        Map<String, Object> row = jdbcTemplate.queryForMap("select * from orders.order_dead_letter");
        assertThat(row).containsEntry("failure_type", "READ_FAILED")
            .containsEntry("payload", "{broken")
            .containsEntry("event_type", null)
            .containsEntry("producer", null)
            .containsEntry("occurred_at", null)
            .containsEntry("aggregate_id", null)
            .containsEntry("correlation_id", null)
            .containsEntry("order_id", null)
            .containsEntry("stack_trace_head", null);
    }

    @Test
    @DisplayName("본문 · 헤더에 NUL 문자가 있어도 저장한다(대체 문자로 바꿔서)")
    void savesNul() {
        deadLetterService.receive(new ReceiveDeadLetterCommand("kidly.product.events", 0, 4L, "k\u0000",
            "product.changed", "e\u0000", "{\"name\":\"a\u0000b\"}", "java.lang.IllegalStateException",
            "m\u0000", null));

        OrderDeadLetter letter = single();
        assertThat(letter.getPayload()).isEqualTo("{\"name\":\"a\uFFFDb\"}");
        assertThat(letter.getMessageKey()).isEqualTo("k\uFFFD");
    }

    private OrderDeadLetter single() {
        List<Long> ids = jdbcTemplate.queryForList("select id from orders.order_dead_letter", Long.class);
        assertThat(ids).hasSize(1);
        return deadLetterRepository.findById(ids.get(0)).orElseThrow();
    }

    private static ReceiveDeadLetterCommand command(long offset, String exceptionClass, String body) {
        return new ReceiveDeadLetterCommand("kidly.payment.events", 1, offset, "42", "payment.completed", "e-1",
            body, exceptionClass, "실패 메시지", "trace");
    }
}
