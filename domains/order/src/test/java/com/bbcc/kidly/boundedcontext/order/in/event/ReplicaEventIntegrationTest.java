package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.order.OrderEventDelivery;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품 · 자녀 이벤트 → 리스너 → 복사본 저장 → 장바구니 담기까지 한 번에 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class ReplicaEventIntegrationTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;

    @Autowired
    private OrderEventDelivery eventDelivery;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상품 · 자녀 이벤트를 받으면 복사본이 저장되고 그 상품을 장바구니에 담을 수 있다")
    void eventsToCart() {
        eventDelivery.deliver(
            new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ProductChangedEvent.Data(1L, SELLER_ID, "상품 A", 1L, ProductStatus.ON_SALE, null,
                    OCCURRED_AT.minusDays(1), false, List.of(
                        new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                            null, null, false, OptionSaleStatus.ON_SALE),
                        new ProductChangedEvent.Option(2L, "색상: 네이비 / 사이즈: 120", "120", 25_000L,
                            null, null, true, OptionSaleStatus.ON_SALE)))),
            new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ChildChangedEvent.Data(1L, BUYER_ID, new BigDecimal("115.0"), new BigDecimal("20.0"),
                    Gender.MALE, LocalDate.of(2019, 1, 1), BodyType.NORMAL, PreferredFit.LOOSE, false)));

        assertThat(productReplicaRepository.findAllByProductId(1L)).hasSize(2);
        assertThat(productReplicaRepository.findByOptionId(1L)).get()
            .extracting(ReplicaProduct::isPurchasable).isEqualTo(true);
        assertThat(childReplicaRepository.findByChildId(1L)).get()
            .extracting(ReplicaChild::getBodyType).isEqualTo("NORMAL");

        AddCartItemResult result = cartService.addItem(BUYER_ID, 1L, 1L, 2);

        assertThat(result.created()).isTrue();
        assertThat(result.line().lineAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("사이즈 없는 상품(옵션 size null)의 product.changed도 복사본으로 저장되고 구매 가능하다")
    void sizeNullProductIsStored() {
        eventDelivery.deliver(new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ProductChangedEvent.Data(3L, SELLER_ID, "양말", 1L, ProductStatus.ON_SALE, null,
                    OCCURRED_AT.minusDays(1), true, List.of(
                        new ProductChangedEvent.Option(30L, "색상: 흰색", null, 5_000L,
                            null, null, false, OptionSaleStatus.ON_SALE)))));

        assertThat(productReplicaRepository.findByOptionId(30L)).get()
            .satisfies(replica -> {
                assertThat(replica.getSize()).isNull();
                assertThat(replica.isPurchasable()).isTrue();
            });
    }

    @Test
    @DisplayName("키가 빠진 깨진 child.changed는 무시되어 기존 자녀 복사본이 그대로, 키 · 몸무게 없는 삭제 이벤트는 삭제 표시만 반영된다")
    void brokenChildEventsKeepOrMarkReplica() {
        publishChild(OCCURRED_AT, new BigDecimal("115.0"), new BigDecimal("20.0"), false);

        publishChild(OCCURRED_AT.plusMinutes(1), null, new BigDecimal("21.0"), false);

        assertThat(childReplicaRepository.findByChildId(1L)).get()
            .satisfies(replica -> {
                assertThat(replica.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
                assertThat(replica.getWeightKg()).isEqualByComparingTo(new BigDecimal("20.0"));
                assertThat(replica.isDeleted()).isFalse();
                assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
            });

        publishChild(OCCURRED_AT.plusMinutes(2), null, null, true);

        assertThat(childReplicaRepository.findByChildId(1L)).get()
            .satisfies(replica -> {
                assertThat(replica.isDeleted()).isTrue();
                assertThat(replica.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
            });
    }

    @Test
    @DisplayName("seller.changed를 받으면 replica_seller에 저장되고, 늦은 이벤트만 갱신되며 상호명 없는 이벤트는 무시된다")
    void sellerEventsToReplica() {
        publishSeller(OCCURRED_AT, "키들리샵", SellerStatus.ACTIVE);

        assertThat(storedSeller()).isEqualTo(Map.of(
            "business_name", "키들리샵", "status", "ACTIVE", "event_occurred_at", Timestamp.valueOf(OCCURRED_AT)));

        publishSeller(OCCURRED_AT.plusMinutes(1), "키들리 아동복", SellerStatus.SUSPENDED);
        publishSeller(OCCURRED_AT, "이전 이름", SellerStatus.ACTIVE);
        publishSeller(OCCURRED_AT.plusMinutes(2), null, SellerStatus.ACTIVE);

        assertThat(storedSeller()).isEqualTo(Map.of("business_name", "키들리 아동복", "status", "SUSPENDED",
            "event_occurred_at", Timestamp.valueOf(OCCURRED_AT.plusMinutes(1))));
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_seller", Long.class))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 product · child · seller 이벤트가 두 번 와도 복사본 행 수 · 값(갱신 시각 포함)이 첫 번째 반영 그대로")
    void sameEventsTwiceKeepReplicas() {
        ProductChangedEvent product = new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ProductChangedEvent.Data(1L, SELLER_ID, "상품 A", 1L, ProductStatus.ON_SALE, null,
                OCCURRED_AT.minusDays(1), false, List.of(
                    new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                        null, null, false, OptionSaleStatus.ON_SALE),
                    new ProductChangedEvent.Option(2L, "색상: 네이비 / 사이즈: 120", "120", 25_000L,
                        null, null, true, OptionSaleStatus.ON_SALE))));
        ChildChangedEvent child = new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ChildChangedEvent.Data(1L, BUYER_ID, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, LocalDate.of(2019, 1, 1), BodyType.NORMAL, PreferredFit.LOOSE, false));
        SellerChangedEvent seller = new SellerChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new SellerChangedEvent.Data(SELLER_ID, "키들리샵", SellerStatus.ACTIVE));

        eventDelivery.deliver(product, child, seller);
        List<Map<String, Object>> first = replicaRows();
        eventDelivery.deliver(product, child, seller);

        assertThat(replicaRows()).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_product", Long.class))
            .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_child", Long.class))
            .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.replica_seller", Long.class))
            .isEqualTo(1L);
    }

    // 세 복사본 테이블의 모든 행 · 모든 칼럼
    private List<Map<String, Object>> replicaRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(jdbcTemplate.queryForList("select * from orders.replica_product order by option_id"));
        rows.addAll(jdbcTemplate.queryForList("select * from orders.replica_child order by child_id"));
        rows.addAll(jdbcTemplate.queryForList("select * from orders.replica_seller order by seller_id"));
        return rows;
    }

    private Map<String, Object> storedSeller() {
        return jdbcTemplate.queryForMap("select business_name, status, event_occurred_at "
            + "from orders.replica_seller where seller_id = ?", SELLER_ID);
    }

    private void publishSeller(LocalDateTime occurredAt, String businessName, SellerStatus status) {
        eventDelivery.deliver(new SellerChangedEvent(
            UUID.randomUUID(), occurredAt, new SellerChangedEvent.Data(SELLER_ID, businessName, status)));
    }

    private void publishChild(LocalDateTime occurredAt, BigDecimal heightCm, BigDecimal weightKg, boolean deleted) {
        eventDelivery.deliver(new ChildChangedEvent(
            UUID.randomUUID(), occurredAt, new ChildChangedEvent.Data(1L, BUYER_ID, heightCm, weightKg, Gender.MALE,
                LocalDate.of(2019, 1, 1), BodyType.NORMAL, PreferredFit.LOOSE, deleted)));
    }
}
