package com.bbcc.kidly.boundedcontext.order.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.ConcurrentCalls;
import com.bbcc.kidly.boundedcontext.order.app.outbox.dto.OutboxRelayResult;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SingleServerJobRunner;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.support.KafkaIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 아웃박스 릴레이(#540)를 실제 PostgreSQL · 테스트용 Kafka로 확인한다. 보내기가 실패하는 경우를 만들려고
 * 선언하지 않은 토픽(자동 생성 꺼짐)으로 메시지를 둔다: 메타데이터 대기(max.block 5초) 뒤 실패한다.
 * 스케줄러는 테스트 설정으로 멈춰 두고 릴레이를 직접 부른다.
 */
@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class OrderOutboxRelayIntegrationTest {

    private static final Duration ONE_RUN_LIMIT = Duration.ofSeconds(8);
    private static final String SECRET_PAYLOAD = "{\"shippingAddress\":\"비밀 주소\"}";

    @Autowired
    private OrderOutboxRelayService relayService;

    @Autowired
    private SingleServerJobRunner jobRunner;

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Kafka가 받지 못하는 메시지면 한 주기가 메타데이터 대기 5초 언저리에서 멈추고, 실패를 기록한 채 보내지 않은 상태로 남는다")
    void oneRunIsBoundedWhenKafkaCannotAccept() {
        Long id = saveToUndeclaredTopic();

        long started = System.nanoTime();
        OutboxRelayResult result = relayService.relay();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result).isEqualTo(new OutboxRelayResult(OutboxRelayResult.Outcome.STOPPED, 1, 0));
        assertThat(elapsed).isLessThan(ONE_RUN_LIMIT);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select sent_at, attempt_count, last_error from orders.order_outbox where id = ?", id);
        assertThat(row).containsEntry("sent_at", null).containsEntry("attempt_count", 1);
        assertThat((String) row.get("last_error")).contains("not present in metadata after 5000 ms");
    }

    @Test
    @DisplayName("보내지 못해도 로그에 본문을 남기지 않는다(KafkaTemplate 기본 실패 로그 포함)")
    void failureLogHasNoPayload(CapturedOutput output) {
        saveToUndeclaredTopic();

        relayService.relay();

        assertThat(output).contains("아웃박스 전송 실패");
        assertThat(output).doesNotContain("비밀 주소");
    }

    @Test
    @DisplayName("두 서버의 릴레이가 동시에 돌면(한 서버에서만, #564) 잠금을 얻은 하나만 보내고 다른 하나는 아무것도 하지 않는다")
    void onlyOneRelayRunsAtATime() throws InterruptedException {
        Long id = saveToUndeclaredTopic();
        List<OutboxRelayResult> results = new CopyOnWriteArrayList<>();
        Runnable relay = () -> jobRunner.run("outbox-relay", () -> results.add(relayService.relay()));

        ConcurrentCalls.Outcome outcome = ConcurrentCalls.run(List.of(relay, relay));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(results).extracting(OutboxRelayResult::outcome).containsExactly(OutboxRelayResult.Outcome.STOPPED);
        assertThat(jdbcTemplate.queryForObject("select attempt_count from orders.order_outbox where id = ?",
            Integer.class, id)).as("보내기를 시도한 릴레이는 하나").isOne();
    }

    private Long saveToUndeclaredTopic() {
        return outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "undeclared-" + UUID.randomUUID(), "1024", SECRET_PAYLOAD)).getId();
    }
}
