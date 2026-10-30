package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 아웃박스 리포지토리가 직접 선언한 조회 · 삭제를 실제 DB로 확인한다.
 * 리포지토리는 서비스 트랜잭션 안에서 불리므로 테스트도 호출을 트랜잭션으로 감싸고, 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class ProductOutboxRepositoryIntegrationTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Autowired
    private ProductOutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // 지운 뒤 남은 행은 요청서에 전체 조회가 없어 표를 직접 읽어 확인한다
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("보내지 않은 메시지만 저장한 순서(id 오름차순)대로 요청한 건수까지 찾는다")
    void findUnsentInIdOrder() {
        UUID first = saveUnsent();
        saveSent();
        UUID third = saveUnsent();
        saveUnsent();

        List<UUID> found = transactionTemplate.execute(status -> outboxRepository.findUnsent(2).stream()
            .map(ProductOutboxMessage::getEventId)
            .toList());

        assertThat(found).containsExactly(first, third);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 보낸 메시지만 지우고, 보내지 않은 메시지와 기준 시각 이후에 보낸 메시지는 남긴다")
    void deleteSentBefore() {
        saveSent(SENT_AT);
        UUID sentAtCutoff = saveSent(SENT_AT.plusDays(1));
        UUID sentLater = saveSent(SENT_AT.plusDays(2));
        UUID unsent = saveUnsent();

        Integer deleted = transactionTemplate.execute(status -> outboxRepository.deleteSentBefore(SENT_AT.plusDays(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select event_id from product.product_outbox", UUID.class))
            .containsExactlyInAnyOrder(sentAtCutoff, sentLater, unsent);
    }

    @Test
    @DisplayName("같은 이벤트 ID로 메시지를 또 저장하면 event_id UNIQUE 제약이 막아 한 줄만 남는다")
    void rejectDuplicateEventId() {
        UUID eventId = saveUnsent();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
            ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(eventId)))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForList("select event_id from product.product_outbox", UUID.class))
            .containsExactly(eventId);
    }

    private UUID saveUnsent() {
        UUID eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
            ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(eventId))));
        return eventId;
    }

    private UUID saveSent() {
        return saveSent(SENT_AT);
    }

    private UUID saveSent(LocalDateTime sentAt) {
        UUID eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            ProductOutboxMessage message = ProductOutboxMessage.create(
                ProductOutboxMessageFixture.productChanged(eventId));
            message.markSent(sentAt);
            outboxRepository.save(message);
        });
        return eventId;
    }
}
