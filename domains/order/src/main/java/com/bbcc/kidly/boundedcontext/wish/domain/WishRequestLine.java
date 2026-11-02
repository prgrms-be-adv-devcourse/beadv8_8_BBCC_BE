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
 * 조르기에 든 공용 장바구니 줄 하나(#701). 옵션 · 조른 수량은 보낸 때 값이고, 가격은 저장하지 않는다(보여 줄 때 지금 가격).
 * 승인 수량은 승인한 줄에만 있다.
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

    @Column(nullable = false)
    private Long wishItemId;

    @Column(nullable = false)
    private Long optionId;

    @Column(nullable = false)
    private int requestedQuantity;

    /**
     * 승인 수량. 승인하지 않은 줄은 null.
     */
    private Integer approvedQuantity;

    WishRequestLine(WishRequest request, Long wishItemId, Long optionId, int requestedQuantity) {
        this.request = request;
        this.wishItemId = wishItemId;
        this.optionId = optionId;
        this.requestedQuantity = requestedQuantity;
    }

    void approve(int quantity) {
        this.approvedQuantity = quantity;
    }

    public boolean isApproved() {
        return approvedQuantity != null;
    }
}
