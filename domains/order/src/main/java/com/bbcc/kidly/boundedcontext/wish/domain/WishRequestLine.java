package com.bbcc.kidly.boundedcontext.wish.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조르기에 든 장바구니 칸 하나(#999). 상품 · 옵션 · 수량은 보낸 때 값이고, 가격은 저장하지 않는다(보여 줄 때 지금 가격).
 * #999 전 조르기의 줄은 공용 장바구니 줄이었으므로 칸 ID가 없다(null).
 */
@Entity
@Table(schema = "orders", name = "wish_request_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishRequestLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private WishRequest request;

    /**
     * 예약한 장바구니 칸 ID. #999 전 줄은 null.
     */
    private Long cartItemId;

    /**
     * 보낸 때 상품 ID. 상품 복사본이 없었으면 null.
     */
    private Long productId;

    @Column(nullable = false)
    private Long optionId;

    /**
     * 보낸 때 칸 수량.
     */
    @Column(name = "requested_quantity", nullable = false)
    private int quantity;

    WishRequestLine(WishRequest request, Long cartItemId, Long productId, Long optionId, int quantity) {
        this.request = request;
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.optionId = optionId;
        this.quantity = quantity;
    }
}
