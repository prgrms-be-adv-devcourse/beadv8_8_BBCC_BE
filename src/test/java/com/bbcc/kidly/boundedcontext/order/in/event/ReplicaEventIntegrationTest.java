package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.cart.CartService;
import com.bbcc.kidly.boundedcontext.order.app.cart.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

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
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private CartService cartService;

    @Test
    @DisplayName("상품 · 자녀 이벤트를 받으면 복사본이 저장되고 그 상품을 장바구니에 담을 수 있다")
    void eventsToCart() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new ProductChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ProductChangedEvent.Data(1L, SELLER_ID, "상품 A", 1L, ProductStatus.ON_SALE, null,
                    OCCURRED_AT.minusDays(1), false, List.of(
                        new ProductChangedEvent.Option(1L, "색상: 네이비 / 사이즈: 110", "110", 25_000L,
                            null, null, false, OptionSaleStatus.ON_SALE),
                        new ProductChangedEvent.Option(2L, "색상: 네이비 / 사이즈: 120", "120", 25_000L,
                            null, null, true, OptionSaleStatus.ON_SALE)))));
            eventPublisher.publish(new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT,
                new ChildChangedEvent.Data(1L, BUYER_ID, new BigDecimal("115.0"), new BigDecimal("20.0"),
                    Gender.MALE, LocalDate.of(2019, 1, 1), BodyType.NORMAL, PreferredFit.LOOSE, false)));
        });

        assertThat(productReplicaRepository.findAllByProductId(1L)).hasSize(2);
        assertThat(productReplicaRepository.findByOptionId(1L)).get()
            .extracting(ReplicaProduct::isPurchasable).isEqualTo(true);
        assertThat(childReplicaRepository.findByChildId(1L)).get()
            .extracting(ReplicaChild::getBodyType).isEqualTo("NORMAL");

        AddCartItemResult result = cartService.addItem(BUYER_ID, 1L, 1L, 2);

        assertThat(result.created()).isTrue();
        assertThat(result.line().lineAmount()).isEqualTo(50_000L);
    }
}
