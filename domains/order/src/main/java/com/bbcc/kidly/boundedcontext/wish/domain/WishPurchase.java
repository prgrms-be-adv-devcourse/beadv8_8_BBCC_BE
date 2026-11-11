package com.bbcc.kidly.boundedcontext.wish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조르기로 산 것(#999): 결제가 끝나 장바구니에서 빠진 조르기 칸 하나. 주문상품은 장바구니 구매 이벤트
 * (cart.item-purchased)의 주문상품 ID로 가리키고, 그 칸이 없던 경우를 위해 (주문 ID, 장바구니 칸 ID)도 둔다
 * (주문상품의 cart_item_id, #998). 조르기는 주문을 읽지 않으므로 주문 쪽 값은 장바구니 구매 이벤트로 받은 것만 둔다.
 * 같은 조르기의 같은 칸은 한 번만 남긴다(유일 제약, 이벤트를 다시 받아도 1회분).
 */
@Entity
@Table(schema = "orders", name = "wish_purchase")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishPurchase extends WishTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Long orderId;

    /**
     * 결제된 주문상품 ID(cart.item-purchased의 orderItemId). 이벤트에 없었으면 null.
     */
    private Long orderItemId;

    @Column(nullable = false)
    private Long cartItemId;

    @Column(nullable = false)
    private Long optionId;

    /**
     * 이번에 산 수량(장바구니 구매 이벤트 값 = 그 주문상품의 수량). 칸에 남은 수량이 아니다.
     */
    @Column(nullable = false)
    private int quantity;

    public static WishPurchase of(Long requestId, Long orderId, Long orderItemId, Long cartItemId, Long optionId,
        int quantity) {
        WishPurchase purchase = new WishPurchase();
        purchase.requestId = requestId;
        purchase.orderId = orderId;
        purchase.orderItemId = orderItemId;
        purchase.cartItemId = cartItemId;
        purchase.optionId = optionId;
        purchase.quantity = quantity;
        return purchase;
    }
}
