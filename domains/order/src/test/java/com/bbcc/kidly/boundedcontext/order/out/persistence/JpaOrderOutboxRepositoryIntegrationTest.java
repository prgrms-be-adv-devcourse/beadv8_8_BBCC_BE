package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.domain.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 아웃박스 저장소(#540): 릴레이가 읽는 안 보낸 메시지(id 순서 · 건수), 한 번에 하나의 릴레이만 돌게 하는
 * 트랜잭션 advisory lock, 보관 기간이 지난 보낸 메시지 정리. 실제 PostgreSQL에서 확인한다.
 */
@IntegrationTest
class JpaOrderOutboxRepositoryIntegrationTest {

    @Autowired
    private OrderOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("안 보낸 메시지를 id 순서로 limit건만 읽고, 보낸 메시지는 건너뛴다")
    void findsUnsentInIdOrder() {
        Long first = save();
        Long sent = save();
        Long second = save();
        save();
        markSent(sent, LocalDateTime.now());

        List<OrderOutboxMessage> unsent = transactionTemplate.execute(status ->
            outboxRepository.findBySentAtIsNullOrderByIdAsc(Limit.of(2)));

        assertThat(unsent).as("셋째 안 보낸 메시지는 limit 밖").extracting(OrderOutboxMessage::getId)
            .containsExactly(first, second);
    }

    @Test
    @DisplayName("릴레이 잠금은 한 트랜잭션만 얻고, 그 트랜잭션이 끝나면 다음 트랜잭션이 얻는다")
    void relayLockIsExclusiveUntilTransactionEnds() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> transactionTemplate.execute(status -> {
            boolean acquired = outboxRepository.tryLockRelay();
            locked.countDown();
            await(release);
            return acquired;
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Boolean whileHeld = transactionTemplate.execute(status -> outboxRepository.tryLockRelay());
        release.countDown();
        Boolean holderAcquired = holder.get(10, TimeUnit.SECONDS);
        Boolean afterRelease = transactionTemplate.execute(status -> outboxRepository.tryLockRelay());

        assertThat(holderAcquired).isTrue();
        assertThat(whileHeld).as("다른 트랜잭션이 쥐고 있으면 기다리지 않고 false").isFalse();
        assertThat(afterRelease).as("트랜잭션이 끝나면 잠금이 풀린다").isTrue();
    }

    @Test
    @DisplayName("정리는 보낸 시각이 기준보다 이른 메시지만 지우고, 안 보낸 메시지는 오래돼도 남긴다")
    void deletesOnlySentBeforeCutoff() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        Long old = save();
        Long recent = save();
        Long unsent = save();
        markSent(old, cutoff.minusSeconds(1));
        markSent(recent, cutoff.plusSeconds(1));
        jdbcTemplate.update("update orders.order_outbox set created_at = ? where id = ?", cutoff.minusDays(30), unsent);

        Integer deleted = transactionTemplate.execute(status -> outboxRepository.deleteSentBefore(cutoff));

        assertThat(deleted).isOne();
        assertThat(jdbcTemplate.queryForList("select id from orders.order_outbox order by id", Long.class))
            .containsExactly(recent, unsent);
    }

    private Long save() {
        return outboxRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "kidly.order.events", "1024", "{}")).getId();
    }

    private void markSent(Long id, LocalDateTime sentAt) {
        jdbcTemplate.update("update orders.order_outbox set sent_at = ?, attempt_count = 1 where id = ?", sentAt, id);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 해제 신호를 기다리다 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
