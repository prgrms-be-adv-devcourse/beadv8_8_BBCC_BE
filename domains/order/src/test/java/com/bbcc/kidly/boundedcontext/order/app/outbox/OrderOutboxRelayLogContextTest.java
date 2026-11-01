package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.CapturedLogs;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventSender;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.out.persistence.OrderOutboxStore;
import com.bbcc.kidly.boundedcontext.order.out.messaging.OrderOutboxMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 아웃박스 릴레이 로그의 추적 값(#603): 보내는 메시지마다 eventId · eventType · correlationId(= 메시지 키)를 MDC에 두고,
 * 다음 메시지 · 실행 뒤에는 남기지 않는다. 보내는 순간의 MDC는 sender 대역이 기록한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderOutboxRelayLogContextTest {

    private static final OrderOutboxProperties PROPERTIES =
        new OrderOutboxProperties(Duration.ofSeconds(1), 100, Duration.ofDays(7));

    // 포트는 실제 구현(OrderOutboxStore)에 Spring Data 저장소 mock을 넣어 쓴다(#642: 엔티티는 out)
    @Mock
    private JpaOrderOutboxRepository jpaRepository;

    @Mock
    private OrderEventSender sender;

    private OrderOutboxRelayService relayService;
    private final List<Map<String, String>> sentWith = new ArrayList<>();

    @BeforeEach
    void setUp() {
        relayService = new OrderOutboxRelayService(new OrderOutboxStore(jpaRepository), sender, PROPERTIES);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("메시지마다 자기 eventId · eventType · correlationId(키) · traceId(= 키), 실패로 멈춰도 그 실패 로그에 값이 붙고 실행 뒤 비운다")
    void perMessageContext() {
        OrderOutboxMessage first = message(1L, "order.created", "1024");
        OrderOutboxMessage second = message(2L, "order.cancelled", "2048");
        givenUnsent(first, second);
        recordMdcOnSend(second);

        try (CapturedLogs logs = CapturedLogs.start()) {
            relayService.relay();

            assertThat(sentWith).containsExactly(
                Map.of("eventId", first.getEventId().toString(), "eventType", "order.created",
                    "correlationId", "1024", "traceId", "1024"),
                Map.of("eventId", second.getEventId().toString(), "eventType", "order.cancelled",
                    "correlationId", "2048", "traceId", "2048"));
            assertThat(logs.mdcOf("아웃박스 전송 실패").orElseThrow())
                .containsEntry("eventId", second.getEventId().toString())
                .containsEntry("correlationId", "2048");
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("이미 traceId가 있으면(요청 안에서 부름) 그대로 두고, 실행 뒤에도 요청 값만 남는다")
    void keepsRequestTraceId() {
        OrderOutboxMessage only = message(1L, "order.created", "1024");
        givenUnsent(only);
        recordMdcOnSend(null);
        MDC.put("traceId", "request-trace");

        relayService.relay();

        assertThat(sentWith).singleElement().satisfies(mdc -> assertThat(mdc)
            .containsEntry("traceId", "request-trace").containsEntry("correlationId", "1024"));
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("traceId", "request-trace"));
    }

    // 보낼 때의 MDC를 기록한다. failing이면 기록한 뒤 실패한다(릴레이는 그 메시지에서 멈춘다)
    private void recordMdcOnSend(OrderOutboxMessage failing) {
        doAnswer(invocation -> {
            sentWith.add(new HashMap<>(MDC.getCopyOfContextMap()));
            if (((OutboxMessage) invocation.getArgument(0)).id().equals(failing.getId())) {
                throw new IllegalStateException("broker down");
            }
            return null;
        }).when(sender).send(any());
    }

    private void givenUnsent(OrderOutboxMessage... messages) {
        when(jpaRepository.findBySentAtIsNullOrderByIdAsc(any())).thenReturn(List.of(messages));
        for (OrderOutboxMessage message : messages) {
            lenient().when(jpaRepository.findById(message.getId())).thenReturn(Optional.of(message));
        }
    }

    private static OrderOutboxMessage message(Long id, String eventType, String key) {
        OrderOutboxMessage message = OrderOutboxMessage.create(UUID.randomUUID(), eventType, "kidly.order.events",
            key, "{}");
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
