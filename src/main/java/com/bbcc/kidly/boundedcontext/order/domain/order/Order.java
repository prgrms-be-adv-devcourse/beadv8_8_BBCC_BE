package com.bbcc.kidly.boundedcontext.order.domain.order;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 애그리거트 루트. 결제대기 → 결제완료 · 취소.
 * 주문상품 변경은 Order의 메서드를 통해 수행한다.
 */
@Entity
@Table(schema = "orders", name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    /**
     * 주문 상태. 결제대기에서만 바뀐다.
     */
    public enum Status {
        PENDING_PAYMENT,
        PAID,
        CANCELLED
    }

    /**
     * 주문 취소 사유.
     */
    public enum CancelReason {
        OUT_OF_STOCK,
        PAYMENT_FAILED,
        PAYMENT_EXPIRED
    }

    /**
     * 주문 당시 배송지. Order에 저장하고 이벤트에는 싣지 않는다.
     */
    @Embeddable
    public record ShippingAddress(
        String recipientName,
        String phone,
        String zipCode,
        String address) {
    }

    /**
     * 주문 설정값(결제 마감 · 자동 구매확정 기간). 설정에서 읽어 도메인 메서드 인자로 넘긴다.
     */
    public record Policy(
        Duration paymentTimeout,
        Duration autoConfirmAfter) {
    }

    /**
     * 주문하기 요청에서 고른 자녀와 판단에 필요한 지금 값.
     * 생성자는 입력 조합의 일관성(스냅샷 · 보호자 ID가 있는지)만 확인한다.
     * 삭제된 자녀 · 남의 자녀를 주문할 수 있는지는 판단하지 않는다(Order.place가 판단).
     *
     * @param snapshot 주문 당시 자녀 값
     * @param memberId 자녀의 보호자 회원 ID (소유 확인용)
     * @param deleted 자녀가 삭제됐는지
     */
    public record RequestedChild(
        OrderItem.ChildSnapshot snapshot,
        Long memberId,
        boolean deleted) {

        public RequestedChild {
            if (snapshot == null || memberId == null) {
                throw new IllegalArgumentException("자녀 스냅샷과 보호자 회원 ID는 null일 수 없습니다.");
            }
        }
    }

    /**
     * 주문하기 요청의 한 줄과 판단에 필요한 지금 값.
     *
     * @param product 주문 당시 상품 값
     * @param purchasable 지금 판매 중인지
     * @param child 고른 자녀. 자녀를 고르지 않으면 null
     * @param quantity 수량
     * @param requestedUnitPrice 화면이 보여 준 단가 (현재 단가와 다르면 409)
     */
    public record RequestedItem(
        OrderItem.ProductSnapshot product,
        boolean purchasable,
        RequestedChild child,
        int quantity,
        long requestedUnitPrice) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CancelReason cancelReason;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(nullable = false)
    private LocalDateTime paymentDueAt;

    private LocalDateTime paymentRequestedAt;

    private LocalDateTime paidAt;

    private Long paymentId;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    private Order(Long memberId, ShippingAddress shippingAddress, LocalDateTime paymentDueAt) {
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
        this.paymentDueAt = paymentDueAt;
        this.status = Status.PENDING_PAYMENT;
    }

    /**
     * 주문하기. 주문 총액 = 주문상품 amount의 합.
     */
    public static Order place(Long memberId, List<RequestedItem> items, ShippingAddress shippingAddress,
        Policy policy, LocalDateTime now) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(OrderErrorCode.EMPTY_ORDER_ITEMS);
        }
        Set<List<Long>> keys = new HashSet<>();
        for (RequestedItem item : items) {
            Long childId = item.child() == null ? null : item.child().snapshot().childId();
            if (!keys.add(Arrays.asList(item.product().optionId(), childId))) {
                throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER_ITEM);
            }
        }

        Order order = new Order(memberId, shippingAddress, now.plus(policy.paymentTimeout()));
        for (RequestedItem item : items) {
            validate(memberId, item);
            OrderItem orderItem = OrderItem.create(
                item.product(), item.child() == null ? null : item.child().snapshot(), item.quantity());
            orderItem.assignTo(order);
            order.items.add(orderItem);
            order.totalAmount = Math.addExact(order.totalAmount, orderItem.getAmount());
        }
        return order;
    }

    /**
     * 결제를 요청한다. 결제대기이고 아직 요청 전일 때만 기록한다(재고 차감 이벤트 중복 · 취소 뒤 도착 대비).
     *
     * @return 이번에 요청했으면 true
     */
    public boolean requestPayment(LocalDateTime now) {
        if (status != Status.PENDING_PAYMENT || paymentRequestedAt != null) {
            return false;
        }
        this.paymentRequestedAt = now;
        return true;
    }

    /**
     * 결제완료로 바꾼다. 결제대기일 때만 바꾸고, 중복 · 취소된 주문은 그대로 둔다.
     * 동시에 두 번 오면 @Version이 나중 쪽을 실패시킨다.
     *
     * @return 이번에 결제완료로 바꿨으면 true
     */
    public boolean markPaid(Long paymentId, LocalDateTime now) {
        if (status != Status.PENDING_PAYMENT) {
            return false;
        }
        this.status = Status.PAID;
        this.paymentId = paymentId;
        this.paidAt = now;
        return true;
    }

    /**
     * 결제대기일 때만 주어진 사유로 취소한다. 결제 마감 만료는 expirePayment를 쓴다.
     * 결제완료 · 이미 취소된 주문은 그대로 둔다(처음 사유 유지).
     *
     * @return 이번에 취소했으면 true
     */
    public boolean cancel(CancelReason reason) {
        if (status != Status.PENDING_PAYMENT) {
            return false;
        }
        this.status = Status.CANCELLED;
        this.cancelReason = reason;
        return true;
    }

    /**
     * 결제 마감 만료로 취소한다. 결제대기이고 마감 시각이 지났을 때(마감 시각 포함)만 바꾼다.
     * 마감 전 · 결제완료 · 이미 취소된 주문은 그대로 둔다.
     *
     * @return 이번에 취소했으면 true
     */
    public boolean expirePayment(LocalDateTime now) {
        if (status != Status.PENDING_PAYMENT || now.isBefore(paymentDueAt)) {
            return false;
        }
        this.status = Status.CANCELLED;
        this.cancelReason = CancelReason.PAYMENT_EXPIRED;
        return true;
    }

    public void recordDelivered(Long sellerId, LocalDateTime at) {
        for (OrderItem item : items) {
            if (Objects.equals(item.getProduct().sellerId(), sellerId)) {
                item.markDelivered(at);
            }
        }
    }

    /**
     * 구매자가 직접 구매확정한다. 주문에 없는 주문상품이면 404, 배송 전이면 409, 이미 확정이면 그대로 둔다.
     *
     * @return 이번에 확정했으면 true
     */
    public boolean confirmItem(Long orderItemId, LocalDateTime now) {
        OrderItem item = findItem(orderItemId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND));
        if (item.getStatus() == OrderItem.Status.CONFIRMED) {
            return false;
        }
        if (item.getDeliveredAt() == null) {
            throw new BusinessException(OrderErrorCode.NOT_DELIVERED);
        }
        return item.confirm(OrderItem.ConfirmType.DIRECT, now);
    }

    /**
     * 배송 완료 후 자동 구매확정 기간이 지났으면(기간 시각 포함) 자동 확정한다.
     * 주문상품 없음 · 배송 전 · 기간 전 · 이미 확정이면 예외 없이 그대로 둔다.
     *
     * @return 이번에 확정했으면 true
     */
    public boolean autoConfirmItem(Long orderItemId, Policy policy, LocalDateTime now) {
        return findItem(orderItemId)
            .filter(item -> item.getDeliveredAt() != null
                && !now.isBefore(item.getDeliveredAt().plus(policy.autoConfirmAfter())))
            .map(item -> item.confirm(OrderItem.ConfirmType.AUTO, now))
            .orElse(false);
    }

    private Optional<OrderItem> findItem(Long orderItemId) {
        return items.stream()
            .filter(item -> Objects.equals(item.getId(), orderItemId))
            .findFirst();
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private static void validate(Long memberId, RequestedItem item) {
        if (item.quantity() < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }
        if (item.requestedUnitPrice() != item.product().unitPrice()) {
            throw new BusinessException(OrderErrorCode.PRICE_CHANGED);
        }
        if (!item.purchasable()) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (Objects.equals(item.product().sellerId(), memberId)) {
            throw new BusinessException(OrderErrorCode.OWN_PRODUCT);
        }
        RequestedChild child = item.child();
        if (child != null && (!Objects.equals(child.memberId(), memberId) || child.deleted())) {
            throw new BusinessException(OrderErrorCode.CHILD_NOT_ORDERABLE);
        }
    }
}
