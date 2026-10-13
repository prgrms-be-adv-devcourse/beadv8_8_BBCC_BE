package com.bbcc.kidly.boundedcontext.order.domain.order;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문상품. 옵션 × 자녀 1행. 상품 · 자녀 스냅샷, 수량 · 금액, 구매확정 상태를 가진다.
 * 변경 메서드는 package-private이므로 다른 패키지에서는 호출할 수 없다.
 * 같은 패키지 내에서 Order를 통해 변경하는 것은 사용 규칙이며, 접근 제어가 Order만을 허용하는 것은 아니다.
 */
@Entity
@Table(schema = "orders", name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    /**
     * 주문상품 상태. 배송 완료 후 한 번만 구매확정된다.
     */
    public enum Status {
        ORDERED,
        CONFIRMED
    }

    /**
     * 구매확정 방식. 주문상품에만 저장하고 이벤트에는 싣지 않는다.
     */
    public enum ConfirmType {
        DIRECT,
        AUTO
    }

    /**
     * 구매확정 때 받는 핏 피드백(작아요 · 딱 맞아요 · 커요). 선택 값.
     */
    public enum FitFeedback {
        SMALL,
        FIT,
        LARGE
    }

    /**
     * 주문 당시 상품 정보. 주문상품마다 남기고 원본이 바뀌어도 바뀌지 않는다. 단가 = 주문 당시 실제 판매 단가.
     */
    @Embeddable
    public record ProductSnapshot(
        Long productId,
        Long optionId,
        Long sellerId,
        String productName,
        String size,
        long unitPrice,
        Long categoryId) {
    }

    /**
     * 주문 당시 자녀 정보. 자녀를 고른 주문상품에만 남긴다.
     */
    @Embeddable
    public record ChildSnapshot(
        Long childId,
        String name,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String bodyType) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Embedded
    private ProductSnapshot product;

    @Embedded
    @AttributeOverride(name = "name", column = @Column(name = "child_name"))
    private ChildSnapshot child;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private LocalDateTime deliveredAt;

    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConfirmType confirmType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FitFeedback fitFeedback;

    /**
     * 주문상품을 만든다. 주문상품 금액 = 주문 당시 단가 × 수량.
     */
    static OrderItem create(ProductSnapshot product, ChildSnapshot child, int quantity) {
        OrderItem item = new OrderItem();
        item.product = product;
        item.child = child;
        item.quantity = quantity;
        item.amount = Math.multiplyExact(product.unitPrice(), quantity);
        item.status = Status.ORDERED;
        return item;
    }

    void assignTo(Order order) {
        this.order = order;
    }

    void markDelivered(LocalDateTime at) {
        if (deliveredAt == null) {
            this.deliveredAt = at;
        }
    }

    void confirm(ConfirmType type, FitFeedback fitFeedback, LocalDateTime now) {
        if (status == Status.CONFIRMED) {
            return;
        }
        if (deliveredAt == null) {
            throw new BusinessException(OrderErrorCode.NOT_DELIVERED);
        }
        this.status = Status.CONFIRMED;
        this.confirmType = type;
        this.fitFeedback = fitFeedback;
        this.confirmedAt = now;
    }
}
