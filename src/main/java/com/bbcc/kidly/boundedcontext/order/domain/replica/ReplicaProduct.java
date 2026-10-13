package com.bbcc.kidly.boundedcontext.order.domain.replica;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 복사본(옵션당 1행). product.changed의 지금 값을 덮어쓰며, 장바구니 담기 · 주문하기가 구매 가능 여부를 판단할 때 쓴다.
 * 재고 수량은 두지 않는다.
 */
@Entity
@Table(schema = "orders", name = "replica_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaProduct extends BaseTimeEntity {

    /**
     * 복사본 입력 값(옵션 1개).
     *
     * @param productStatus 상품 판매 상태 이름. 모르는 값이면 구매 불가
     * @param occurredAt 이벤트 발생 시각. 저장된 값보다 늦을 때만 반영한다
     */
    public record Values(
        Long optionId,
        Long productId,
        Long sellerId,
        String productName,
        String size,
        long unitPrice,
        Long categoryId,
        String productStatus,
        boolean soldOut,
        LocalDateTime occurredAt) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long optionId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, length = 20)
    private String size;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false)
    private long unitPrice;

    private Long categoryId;

    @Column(nullable = false, length = 30)
    private String productStatus;

    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    @Column(nullable = false)
    private LocalDateTime eventOccurredAt;

    private static final String ON_SALE = "ON_SALE";

    public static ReplicaProduct create(Values values) {
        ReplicaProduct replica = new ReplicaProduct();
        replica.optionId = values.optionId();
        replica.apply(values);
        return replica;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓴다.
     *
     * @return 반영했으면 true
     */
    public boolean applyIfNewer(Values values) {
        if (!values.occurredAt().isAfter(eventOccurredAt)) {
            return false;
        }
        apply(values);
        return true;
    }

    /**
     * 같은 상품의 이벤트에서 빠진 옵션을 품절로 바꾼다. 늦게 발생한 이벤트일 때만 반영한다.
     *
     * @return 반영했으면 true
     */
    public boolean markSoldOutIfNewer(LocalDateTime occurredAt) {
        if (!occurredAt.isAfter(eventOccurredAt)) {
            return false;
        }
        soldOut = true;
        eventOccurredAt = occurredAt;
        return true;
    }

    public boolean isPurchasable() {
        return ON_SALE.equals(productStatus) && !soldOut;
    }

    public boolean isSoldBy(Long memberId) {
        return sellerId.equals(memberId);
    }

    public long unitPrice() {
        return unitPrice;
    }

    private void apply(Values values) {
        productId = values.productId();
        sellerId = values.sellerId();
        productName = values.productName();
        size = values.size();
        unitPrice = values.unitPrice();
        categoryId = values.categoryId();
        productStatus = values.productStatus();
        soldOut = values.soldOut();
        eventOccurredAt = values.occurredAt();
    }
}
