package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 복사본 반영을 운영과 같은 길로 확인한다. 테스트 설정은 Kafka 수신 스위치가 꺼져 있어(kidly.community.kafka.consumer.enabled=false)
 * Spring 이벤트로 받는다: 한 트랜잭션에서 발행하고 커밋된 뒤 받는 쪽이 새 트랜잭션에서 처리한다. 결과는 DB에서 다시 읽는다.
 */
@IntegrationTest
class ProductReplicaIntegrationTest {

    private static final long PRODUCT_ID = 7L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.now().minusHours(1);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("스위치가 꺼져 있으면 Spring 이벤트로 받아 복사본을 저장한다(표시 상태 포함)")
    void springEventStored() {
        deliver(changed("상품 A", ProductStatus.ON_SALE, true, OCCURRED_AT));

        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("name", "상품 A").containsEntry("display_status", "SOLD_OUT");
            assertThat(row).containsEntry("thumbnail_url", "https://img/7.png");
        });
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 받아도 행 1개 · 값 1회분이고, 두 번째는 수정 시각도 바꾸지 않는다")
    void duplicateAppliedOnce() {
        ProductChangedEvent event = changed("상품 A", ProductStatus.ON_SALE, false, OCCURRED_AT);

        deliver(event);
        List<Map<String, Object>> first = rows();
        deliver(event);

        assertThat(rows()).hasSize(1).isEqualTo(first);
    }

    @Test
    @DisplayName("늦게 발생한 이벤트는 덮어쓰고, 그 뒤 옛 이벤트가 뒤늦게 와도 값을 유지한다")
    void olderEventAfterNewerKeepsValue() {
        deliver(changed("상품 A", ProductStatus.ON_SALE, false, OCCURRED_AT));
        deliver(changed("상품 B", ProductStatus.STOPPED, false, OCCURRED_AT.plusMinutes(1)));
        List<Map<String, Object>> newer = rows();

        deliver(changed("상품 C", ProductStatus.ON_SALE, false, OCCURRED_AT.plusSeconds(30)));

        assertThat(rows()).isEqualTo(newer).singleElement().satisfies(row ->
            assertThat(row).containsEntry("name", "상품 B").containsEntry("display_status", "SALE_ENDED"));
    }

    private void deliver(ProductChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("select product_id, name, thumbnail_url, display_status, source_occurred_at, "
            + "created_at, updated_at from community.replica_product where product_id = ?", PRODUCT_ID);
    }

    private static ProductChangedEvent changed(String name, ProductStatus status, boolean soldOut,
        LocalDateTime occurredAt) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(PRODUCT_ID, 2L, name,
            3L, status, "https://img/7.png", occurredAt.minusDays(1), false, List.of(
                new ProductChangedEvent.Option(70L, "색상: 네이비 / 사이즈: 110", "110", 25_000L, null, null, soldOut,
                    OptionSaleStatus.ON_SALE))));
    }
}
