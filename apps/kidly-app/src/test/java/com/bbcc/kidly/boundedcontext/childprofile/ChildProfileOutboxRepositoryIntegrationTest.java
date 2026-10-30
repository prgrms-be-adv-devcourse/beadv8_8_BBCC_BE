package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀 프로필 아웃박스 리포지토리가 직접 선언한 조회 · 삭제 · 릴레이 잠금을 실제 DB로 확인한다.
 * 리포지토리는 서비스 트랜잭션 안에서 불리므로 테스트도 호출을 트랜잭션으로 감싸고, 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ChildProfileOutboxRepositoryIntegrationTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private ChildProfileOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 지운 뒤 남은 행은 요청서에 전체 조회가 없어 표를 직접 읽어 확인한다
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("보내지 않은 메시지만 저장한 순서(id 오름차순)대로 요청한 건수까지 찾는다")
    void findUnsentInIdOrder() {
        var first = saveUnsent();
        saveSent(SENT_AT);
        var third = saveUnsent();
        saveUnsent();

        List<UUID> found = transactionTemplate.execute(status -> outboxRepository.findUnsent(2).stream()
            .map(ChildProfileOutboxMessage::getEventId)
            .toList());

        assertThat(found).containsExactly(first, third);
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
    @DisplayName("같은 이벤트 ID로 메시지를 또 저장하면 event_id UNIQUE 제약이 막아 한 줄만 남는다")
    void rejectDuplicateEventId() {
        var eventId = saveUnsent();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
            ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(eventId)))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(eventIds()).containsExactly(eventId);
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

    // 잠금을 쥔 트랜잭션을 테스트가 풀어 줄 때까지 열어 둔다
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
            ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(eventId))));
        return eventId;
    }

    private UUID saveSent(LocalDateTime sentAt) {
        var eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            var message = ChildProfileOutboxMessage.create(ChildProfileOutboxMessageFixture.childChanged(eventId));
            message.markSent(sentAt);
            outboxRepository.save(message);
        });
        return eventId;
    }

    private List<UUID> eventIds() {
        return jdbcTemplate.queryForList("select event_id from childprofile.child_profile_outbox", UUID.class);
    }
}
