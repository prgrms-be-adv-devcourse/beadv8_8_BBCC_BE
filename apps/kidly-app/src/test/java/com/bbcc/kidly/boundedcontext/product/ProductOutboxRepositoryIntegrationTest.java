package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    private UUID saveUnsent() {
        UUID eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> outboxRepository.save(
            ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(eventId))));
        return eventId;
    }

    private void saveSent() {
        transactionTemplate.executeWithoutResult(status -> {
            ProductOutboxMessage message = ProductOutboxMessage.create(
                ProductOutboxMessageFixture.productChanged(UUID.randomUUID()));
            message.markSent(SENT_AT);
            outboxRepository.save(message);
        });
    }
}
