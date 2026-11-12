package com.bbcc.kidly.boundedcontext.activity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.bbcc.kidly.boundedcontext.activity.app.CommunityEventRelayService.RelayRun;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import com.bbcc.kidly.boundedcontext.activity.app.port.CommunityEventSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 릴레이: id 순으로 보내고 보낸 행은 지운다. 실패하면 시도 +1 · 백오프(1초부터 두 배, 상한 60초)를 남기고 그 주기를 멈춰 뒤 행이 앞지르지
 * 않는다. 아직 다시 보낼 때가 아닌 앞 행이 있으면 뒤 행도 보내지 않는다. 시도 상한(10)에 닿으면 격리하고 다음 행으로 넘어간다.
 */
class CommunityEventRelayServiceTest {

    private final FakeOutbox outbox = new FakeOutbox();
    private final FakeSender publisher = new FakeSender();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private boolean lockFree = true;
    private final CommunityEventRelayService service = new CommunityEventRelayService(outbox, publisher,
        work -> {
            if (!lockFree) {
                return false;
            }
            work.run();
            return true;
        },
        new CommunityEventMetrics(registry, outbox),
        new CommunityEventRelayProperties(Duration.ofSeconds(1), 100, 10, Duration.ofSeconds(1),
            Duration.ofSeconds(60), Duration.ofSeconds(10)));

    @Test
    @DisplayName("id 순으로 보내고 보낸 행은 바로 지운다")
    void sendsInOrderAndDeletes() {
        outbox.add(1, "a", 0, past());
        outbox.add(2, "b", 0, past());
        outbox.add(3, "a", 0, past());

        RelayRun run = service.relay();

        assertThat(run).isEqualTo(new RelayRun(3, 0, 0, false));
        assertThat(publisher.sent).containsExactly(1L, 2L, 3L);
        assertThat(outbox.rows).isEmpty();
    }

    @Test
    @DisplayName("보내다 실패하면 시도 +1 · 다음 시각 1초 뒤 · 실패 종류를 남기고 그 주기를 멈춘다(뒤 행은 보내지 않음)")
    void failureStopsTheCycle() {
        outbox.add(1, "a", 0, past());
        outbox.add(2, "b", 0, past());
        publisher.failing.add(1L);
        LocalDateTime before = LocalDateTime.now();

        RelayRun run = service.relay();

        assertThat(run).isEqualTo(new RelayRun(0, 1, 0, false));
        assertThat(publisher.sent).isEmpty();
        assertThat(outbox.rows.get(1L).attempts()).isEqualTo(1);
        assertThat(outbox.rows.get(1L).nextAttemptAt()).isCloseTo(before.plusSeconds(1), within(1, ChronoUnit.SECONDS));
        assertThat(outbox.failureTypes.get(0)).isEqualTo("TimeoutException");
        assertThat(outbox.rows).containsKey(2L);
        assertThat(registry.get(CommunityEventMetrics.OUTBOX_SEND_FAILURES).counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("앞 행이 아직 다시 보낼 때가 아니면 뒤 행도 보내지 않는다(순서 유지)")
    void notDueRowBlocks() {
        outbox.add(1, "a", 1, LocalDateTime.now().plusSeconds(30));
        outbox.add(2, "b", 0, past());

        RelayRun run = service.relay();

        assertThat(run).isEqualTo(new RelayRun(0, 0, 0, false));
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("백오프는 1초부터 두 배씩, 60초에서 멈춘다")
    void backoffCaps() {
        assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(service.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(service.backoff(6)).isEqualTo(Duration.ofSeconds(32));
        assertThat(service.backoff(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(service.backoff(40)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("10번째 실패면 격리하고(행은 남김) 다음 행으로 넘어간다")
    void quarantinesAtMaxAttempts() {
        outbox.add(1, "a", 9, past());
        outbox.add(2, "a", 0, past());
        publisher.failing.add(1L);

        RelayRun run = service.relay();

        assertThat(run).isEqualTo(new RelayRun(1, 0, 1, false));
        assertThat(outbox.quarantined).containsExactly(1L);
        assertThat(publisher.sent).containsExactly(2L);
        assertThat(outbox.rows).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("다른 서버가 잠금을 쥐면 건너뛴다(보내지 않음)")
    void skipsWhenLocked() {
        outbox.add(1, "a", 0, past());
        lockFree = false;

        assertThat(service.relay().skipped()).isTrue();
        assertThat(publisher.sent).isEmpty();
    }

    private static LocalDateTime past() {
        return LocalDateTime.now().minusSeconds(1);
    }

    private static final class FakeOutbox implements EventOutbox {

        private final TreeMap<Long, PendingMessage> rows = new TreeMap<>();
        private final List<Long> quarantined = new ArrayList<>();
        private final List<String> failureTypes = new ArrayList<>();

        void add(long id, String key, int attempts, LocalDateTime nextAttemptAt) {
            rows.put(id, new PendingMessage(id, UUID.randomUUID(), "review.changed", key, "{}", attempts,
                nextAttemptAt));
        }

        @Override
        public void append(List<NewMessage> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingMessage> findPending(int limit) {
            return rows.values().stream().limit(limit).toList();
        }

        @Override
        public void delete(long id, UUID eventId) {
            rows.remove(id);
        }

        @Override
        public void recordFailure(long id, UUID eventId, LocalDateTime nextAttemptAt, String failureType) {
            PendingMessage row = rows.get(id);
            rows.put(id, new PendingMessage(id, row.eventId(), row.eventType(), row.key(), row.payload(),
                row.attempts() + 1, nextAttemptAt));
            failureTypes.add(failureType);
        }

        @Override
        public void quarantine(long id, UUID eventId, LocalDateTime quarantinedAt, String failureType) {
            rows.remove(id);
            quarantined.add(id);
        }

        @Override
        public long countPending() {
            return rows.size();
        }

        @Override
        public long countQuarantined() {
            return quarantined.size();
        }

        @Override
        public LocalDateTime findOldestPendingCreatedAt() {
            return null;
        }
    }

    private final class FakeSender implements CommunityEventSender {

        private final List<Long> sent = new ArrayList<>();
        private final Set<Long> failing = new HashSet<>();

        @Override
        public void send(String key, String eventType, UUID eventId, String payload, Duration timeout) {
            Long id = outbox.rows.entrySet().stream().filter(entry -> entry.getValue().eventId().equals(eventId))
                .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
            if (failing.contains(id)) {
                throw new SendFailedException("TimeoutException");
            }
            sent.add(id);
        }
    }
}
