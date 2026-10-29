package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxCleanupService;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정리 서비스가 실제 DB에서 자기 트랜잭션으로 벌크 DELETE를 실행하는지 본다.
 * 테스트 설정에서 정리 스케줄을 껐으므로 서비스를 직접 부른다.
 */
@IntegrationTest
class ProductOutboxCleanupIntegrationTest {

    @Autowired
    private ProductOutboxCleanupService cleanupService;

    @Autowired
    private ProductOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("보관 기간(7일)보다 먼저 보낸 메시지만 지우고, 그 안에 보낸 메시지와 보내지 않은 메시지는 남긴다")
    void deleteOnlyMessagesSentBeforeRetention() {
        UUID sentEightDaysAgo = saveSent(LocalDateTime.now().minusDays(8));
        UUID sentSixDaysAgo = saveSent(LocalDateTime.now().minusDays(6));
        UUID unsent = UUID.randomUUID();
        outboxRepository.save(ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(unsent)));

        cleanupService.cleanUp();

        assertThat(jdbcTemplate.queryForList("select event_id from product.product_outbox", UUID.class))
            .containsExactlyInAnyOrder(sentSixDaysAgo, unsent)
            .doesNotContain(sentEightDaysAgo);
    }

    private UUID saveSent(LocalDateTime sentAt) {
        UUID eventId = UUID.randomUUID();
        ProductOutboxMessage message = ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(eventId));
        message.markSent(sentAt);
        outboxRepository.save(message);
        return eventId;
    }
}
