package com.bbcc.kidly.boundedcontext.settlement.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 정산 에러 처리기가 다시 해도 같은 실패만 바로 DLT로 보내고, 나머지는 재시도하는지 본다.
 * 리스너가 던진 예외는 ListenerExecutionFailedException에 싸여 오므로 같은 모양으로 넘긴다.
 */
class SettlementKafkaErrorHandlerTest {

    private final ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
    private final DefaultErrorHandler errorHandler = SettlementKafkaListenerConfig.errorHandler(recoverer);
    private final ConsumerRecord<String, String> record =
        new ConsumerRecord<>("kidly.payment.events", 0, 0L, "31", "{}");

    @Test
    @DisplayName("정산서와 맞지 않는 결과(IllegalStateException)와 읽지 못한 본문(JacksonException)은 재시도 없이 DLT로 보낸다")
    void notRetryable() {
        JacksonException unreadable = catchJackson();

        assertThat(handle(new IllegalStateException("지급 결과가 정산서와 다릅니다"))).isTrue();
        assertThat(handle(unreadable)).isTrue();

        then(recoverer).should(times(2)).accept(any(), any());
    }

    @Test
    @DisplayName("같은 구매확정이 동시에 들어와 생긴 유니크 위반은 재시도한다. 다시 하면 이미 있는 항목이라 건너뛴다")
    void retryUniqueViolation() {
        assertThat(handle(new DataIntegrityViolationException("uk_settlement_item_order_item_id"))).isFalse();

        then(recoverer).should(never()).accept(any(), any());
    }

    private boolean handle(Exception cause) {
        return errorHandler.handleOne(new ListenerExecutionFailedException("실패", cause), record,
            mock(Consumer.class), mock(MessageListenerContainer.class));
    }

    private static JacksonException catchJackson() {
        try {
            JsonMapper.builder().build().readTree("{");
        } catch (JacksonException e) {
            return e;
        }
        throw new AssertionError("JacksonException이 나야 합니다");
    }
}
