package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.domain.StockDeductionFailureCode;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품이 발행한 이벤트를 커밋 직전에 아웃박스에 저장하는지 실제 트랜잭션으로 확인한다.
 * 저장은 발행한 트랜잭션 안에서 일어나야 하므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class ProductOutboxRecorderIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 아웃박스 저장이 실패할 때 같은 트랜잭션의 업무가 남는지 보려고, 상품 스키마의 판매자 복사본을 업무 저장으로 쓴다
    @Autowired
    private ProductReplicaSellerRepository replicaSellerRepository;

    @ParameterizedTest(name = "{0}")
    @MethodSource("productEvents")
    @DisplayName("상품 이벤트를 발행한 트랜잭션이 커밋되면 그 이벤트의 종류 · 키로 아웃박스 행이 하나 남는다")
    void recordProductEventOnCommit(DomainEvent event, String eventType, String messageKey) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertThat(outboxRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("event_id", event.eventId())
            .containsEntry("event_type", eventType)
            .containsEntry("topic", "kidly.product.events")
            .containsEntry("message_key", messageKey));
    }

    @Test
    @DisplayName("상품 이벤트를 발행한 트랜잭션이 롤백되면 아웃박스 행이 남지 않는다")
    void recordNothingOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(productChanged());
            status.setRollbackOnly();
        });

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 같은 트랜잭션에서 커밋돼도 아웃박스에 저장하지 않는다")
    void ignoreOtherDomainEvent() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(
            ProductReplicaSellerFixture.sellerApprovedEvent()));

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    @DisplayName("아웃박스 저장이 실패하면 같은 트랜잭션의 업무도 커밋되지 않는다")
    void rollBackBusinessWhenRecordFails() {
        ProductChangedEvent event = productChanged();

        // 같은 이벤트를 두 번 발행하면 두 번째 저장이 event_id UNIQUE에 걸려 커밋 직전에 실패한다
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            replicaSellerRepository.save(ProductReplicaSeller.create(ProductReplicaSellerFixture.sellerApproved()));
            eventPublisher.publish(event);
            eventPublisher.publish(event);
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(replicaSellerRepository.findById(ProductReplicaSellerFixture.SELLER_ID)).isEmpty();
        assertThat(outboxRows()).isEmpty();
    }

    static Stream<Arguments> productEvents() {
        return Stream.of(
            Arguments.of(Named.of("상품 정보 변경됨", productChanged()), "product.changed", "1"),
            Arguments.of(Named.of("재고 차감됨", new StockDeductedEvent(UUID.randomUUID(), OCCURRED_AT,
                new StockDeductedEvent.Data(77L))), "stock.deducted", "77"),
            Arguments.of(Named.of("재고 차감 실패", new StockDeductionFailedEvent(UUID.randomUUID(), OCCURRED_AT,
                new StockDeductionFailedEvent.Data(78L, StockDeductionFailureCode.OUT_OF_STOCK, List.of(
                    new StockDeductionFailedEvent.Failure(781L, StockDeductionFailureCode.OUT_OF_STOCK))))),
                "stock.deduction-failed", "78"));
    }

    // 상품 1의 판매 시작으로 나간 상품 정보 변경됨이다. 아웃박스는 이벤트 값을 그대로 옮기므로 옵션은 비워 둔다
    private static ProductChangedEvent productChanged() {
        return new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ProductChangedEvent.Data(
            1L, 2L, "데일리 맨투맨", 3L, ProductStatus.ON_SALE, null, OCCURRED_AT, false, List.of()));
    }

    private List<Map<String, Object>> outboxRows() {
        return jdbcTemplate.queryForList(
            "select event_id, event_type, topic, message_key from product.product_outbox order by id");
    }
}
