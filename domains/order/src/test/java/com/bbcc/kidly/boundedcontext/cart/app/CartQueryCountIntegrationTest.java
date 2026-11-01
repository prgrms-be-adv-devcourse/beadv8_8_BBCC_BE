package com.bbcc.kidly.boundedcontext.cart.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.support.QueryCounter;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 장바구니 조회가 줄 · 판매자 수와 상관없이 같은 수의 쿼리로 끝나는지(줄마다 상품 · 판매자를 읽지 않는지) 본다.
 * 쿼리 수는 {@link QueryCounter}로 센다(공용 통합 테스트 컨텍스트 그대로, #586).
 */
@IntegrationTest
class CartQueryCountIntegrationTest {

    private static final Long SINGLE_BUYER_ID = 101L;
    private static final Long MANY_BUYER_ID = 102L;
    private static final int MANY = 5;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 9, 0);

    @Autowired
    private CartService cartService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private SellerReplicaRepository sellerReplicaRepository;

    @Autowired
    private QueryCounter queryCounter;

    @Test
    @DisplayName("줄 · 판매자가 1개든 5개든 쿼리 수가 같고, 각 줄은 자기 판매자의 상호명을 갖는다")
    void sameQueryCountRegardlessOfLineCount() {
        addLine(SINGLE_BUYER_ID, 0);
        for (int i = 1; i <= MANY; i++) {
            addLine(MANY_BUYER_ID, i);
        }

        long singleCount = countStatements(SINGLE_BUYER_ID);
        long manyCount = countStatements(MANY_BUYER_ID);

        assertThat(manyCount).isEqualTo(singleCount);
        assertThat(singleCount).as("장바구니 줄 1번 + 상품 복사본 1번 + 판매자 상호명 1번").isEqualTo(3);
        assertThat(cartService.getCart(MANY_BUYER_ID).items())
            .extracting(CartView.Line::sellerId, CartView.Line::sellerName)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(205L, "판매자 5"),
                org.assertj.core.groups.Tuple.tuple(204L, "판매자 4"),
                org.assertj.core.groups.Tuple.tuple(203L, "판매자 3"),
                org.assertj.core.groups.Tuple.tuple(202L, "판매자 2"),
                org.assertj.core.groups.Tuple.tuple(201L, "판매자 1"));
    }

    private long countStatements(Long memberId) {
        return queryCounter.count(() -> cartService.getCart(memberId));
    }

    /**
     * 옵션 · 상품 · 판매자가 index마다 다른 줄 하나(판매자 200 + index, 상호명 "판매자 index").
     */
    private void addLine(Long memberId, int index) {
        long optionId = 100L + index;
        long sellerId = 200L + index;
        productReplicaRepository.save(ReplicaProduct.create(new ReplicaProduct.Values(optionId, optionId, sellerId,
            "상품 " + index, null, "110", 10_000L, 1L, "ON_SALE", "ON_SALE", false, OCCURRED_AT)));
        sellerReplicaRepository.save(ReplicaSeller.create(
            new ReplicaSeller.Values(sellerId, "판매자 " + index, "ACTIVE", OCCURRED_AT)));
        cartItemRepository.save(CartItem.create(memberId, optionId, null, 1));
    }
}
