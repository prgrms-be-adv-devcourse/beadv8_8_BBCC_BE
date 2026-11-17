package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class SellerOutboxRepositoryIntegrationTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private SellerOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("보내지 않은 메시지만 저장한 순서(id 오름차순)대로 요청한 건수까지 찾는다")
    void findUnsentInIdOrder() {
        var first = saveUnsent();
        saveSent(SENT_AT);
        var third = saveUnsent();
        var recent = saveUnsent();
        jdbcTemplate.update("update seller.seller_outbox set created_at = ? where event_id <> ?",
            LocalDateTime.now().minusMinutes(6), recent);

        List<UUID> found = transactionTemplate.execute(status -> outboxRepository
            .findUnsentCreatedBefore(LocalDateTime.now().minusMinutes(5), 2).stream()
            .map(SellerOutboxMessage::getEventId)
            .toList());

        assertThat(found).containsExactly(first, third);
    }

    @Test
    @DisplayName("선택한 메시지만 전송 시각과 시도 횟수를 일괄 기록한다")
    void markSentInBatch() {
        var first = saveUnsent();
        var second = saveUnsent();
        var ids = jdbcTemplate.queryForList("select id from seller.seller_outbox where event_id in (?, ?)",
            Long.class, first, second);
        transactionTemplate.executeWithoutResult(status -> outboxRepository.markSent(ids, SENT_AT));
        assertThat(jdbcTemplate.queryForObject("select count(*) from seller.seller_outbox "
            + "where sent_at = ? and attempt_count = 1", Integer.class, SENT_AT)).isEqualTo(2);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 보낸 메시지만 지우고, 보내지 않은 메시지와 기준 시각 이후에 보낸 메시지는 남긴다")
    void deleteSentBefore() {
        saveSent(SENT_AT);
        var sentAtCutoff = saveSent(SENT_AT.plusDays(1));
        var sentLater = saveSent(SENT_AT.plusDays(2));
        var unsent = saveUnsent();

        Integer deleted = transactionTemplate.execute(
            status -> outboxRepository.deleteSentBefore(SENT_AT.plusDays(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(eventIds()).containsExactlyInAnyOrder(sentAtCutoff, sentLater, unsent);
    }

    @Test
    @DisplayName("릴레이 잠금은 한 트랜잭션만 얻고, 그 트랜잭션이 끝나면 다시 얻을 수 있다")
    void relayLockIsExclusiveUntilTransactionEnds() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> held = holder.submit(() -> transactionTemplate.execute(status -> {
                var acquired = outboxRepository.tryLockRelay();
                locked.countDown();
                awaitQuietly(release);
                return acquired;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            Boolean whileHeld = transactionTemplate.execute(status -> outboxRepository.tryLockRelay());
            release.countDown();
            Boolean firstAcquired = held.get(10, TimeUnit.SECONDS);
            Boolean afterRelease = transactionTemplate.execute(status -> outboxRepository.tryLockRelay());

            assertThat(firstAcquired).isTrue();
            assertThat(whileHeld).isFalse();
            assertThat(afterRelease).isTrue();
        } finally {
            release.countDown();
            holder.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UUID saveUnsent() {
        var eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
            SellerOutboxMessage.create(SellerOutboxMessageFixture.changed(eventId))));
        return eventId;
    }

    private UUID saveSent(LocalDateTime sentAt) {
        var eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            var message = SellerOutboxMessage.create(SellerOutboxMessageFixture.changed(eventId));
            message.markSent(sentAt);
            outboxRepository.save(message);
        });
        return eventId;
    }

    private List<UUID> eventIds() {
        return jdbcTemplate.queryForList("select event_id from seller.seller_outbox", UUID.class);
    }
}
