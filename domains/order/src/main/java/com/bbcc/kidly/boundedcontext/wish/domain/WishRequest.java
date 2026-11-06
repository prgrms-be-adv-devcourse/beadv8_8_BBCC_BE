package com.bbcc.kidly.boundedcontext.wish.domain;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException.Rule;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조르기 한 건(#701): 한 자녀의 공용 장바구니 줄 여러 개 + 메시지 한 줄(선택). 보호자가 한 번만 승인하거나 거절한다(@Version).
 * <ul>
 *   <li>REQUESTED: 든 줄이 모두 잠긴다(다른 조르기에 넣거나 수량을 바꾸거나 뺄 수 없다)</li>
 *   <li>APPROVED: 고른 줄만 고른 수량으로 승인한다. 바로 결제(ORDER)면 주문 ID를 기록하고, 그 주문의 결제 결과가 정해질 때까지
 *       승인한 줄은 잠긴 채로 둔다(결제되면 PAID · 공용 장바구니에서 빠짐, 결제 전에 취소되면 CANCELLED · 잠금만 풀림).
 *       보호자 장바구니로 옮기기(CART)는 승인과 함께 끝난다</li>
 *   <li>REJECTED: 사유 하나를 고른다. 잠금이 모두 풀린다</li>
 *   <li>CANCELLED: 보낸 사람이 처리 전에 철회했다(#968). 잠금이 모두 풀리고 줄은 공용 장바구니에 남는다(다시 조를 수 있다)</li>
 * </ul>
 * 승인 · 거절 · 철회는 REQUESTED에서만 되고, 동시에 오면 @Version으로 먼저 저장한 쪽만 남는다.
 * 줄 잠금 · 해제 · 구매 반영은 줄({@link WishItem})의 메서드로 하고, 어느 줄을 그렇게 할지는 이 조르기가 정한다.
 */
@Entity
@Table(schema = "orders", name = "wish_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishRequest extends WishTimeEntity {

    public enum Status {
        REQUESTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    /**
     * 승인 뒤 구매 방법.
     */
    public enum Checkout {
        ORDER,
        CART
    }

    /**
     * 바로 결제한 주문의 결과. 정해지기 전에는 null.
     */
    public enum OrderResult {
        PAID,
        CANCELLED
    }

    /**
     * 거절 사유. 고를 수 있는 목록은 app의 WishPolicy가 정한다.
     */
    public enum RejectReason {
        TOO_EXPENSIVE,
        ALREADY_HAVE,
        NEXT_TIME,
        OTHER
    }

    /**
     * 승인할 줄 하나.
     *
     * @param quantity 승인 수량(조른 수량과 달라도 된다, 1~99)
     */
    public record Approval(Long wishItemId, int quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddedBy requestedByType;

    /**
     * 부모면 회원 ID, 자녀면 자녀 프로필 ID.
     */
    @Column(nullable = false)
    private Long requestedById;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Checkout checkout;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderResult orderResult;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RejectReason rejectReason;

    /**
     * 승인 · 거절한 보호자 회원 ID.
     */
    private Long decidedById;

    private LocalDateTime decidedAt;

    /**
     * 철회 시각(#968). 철회한 사람은 보낸 사람(requestedByType · requestedById)이다.
     */
    private LocalDateTime cancelledAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WishRequestLine> lines = new ArrayList<>();

    /**
     * 조르기를 만든다. 줄 잠금은 저장해 ID가 생긴 뒤 {@link #lockItems}로 한다.
     *
     * @param message 앞뒤 공백을 지운 메시지. 비었으면 null로 둔다
     * @param maxMessageLength 메시지 최대 길이(정책)
     */
    public static WishRequest send(Long childId, AddedBy requestedByType, Long requestedById, String message,
        int maxMessageLength, List<WishItem> items) {
        String trimmed = message == null || message.isBlank() ? null : message.strip();
        if (items.isEmpty() || (trimmed != null && trimmed.length() > maxMessageLength)) {
            throw new WishRuleViolationException(Rule.INVALID_WISH_REQUEST);
        }
        Set<Long> seen = new HashSet<>();
        for (WishItem item : items) {
            if (!seen.add(item.getId()) || !item.getChildId().equals(childId)
                || item.getStatus() != WishItem.Status.ACTIVE) {
                throw new WishRuleViolationException(Rule.INVALID_WISH_REQUEST);
            }
            item.requireUnlocked();
        }
        WishRequest request = new WishRequest();
        request.childId = childId;
        request.requestedByType = requestedByType;
        request.requestedById = requestedById;
        request.message = trimmed;
        request.status = Status.REQUESTED;
        for (WishItem item : items) {
            request.lines.add(new WishRequestLine(request, item.getId(), item.getOptionId(), item.getQuantity()));
        }
        return request;
    }

    /**
     * 든 줄을 이 조르기로 잠근다(저장 뒤). 줄은 send에 준 것과 같다.
     */
    public void lockItems(List<WishItem> items) {
        items.forEach(item -> item.lockFor(id));
    }

    /**
     * 고른 줄만 고른 수량으로 승인한다. 승인하지 않은 줄은 잠금을 푼다. 장바구니로 옮기기면 승인한 줄도 푼다(여기서 끝남).
     *
     * @param items 이 조르기의 줄(잠금을 바꿀 대상)
     * @return 승인한 줄(조르기 줄 순서)
     */
    public List<WishRequestLine> approve(List<Approval> approvals, Checkout checkout, Long deciderId,
        List<WishItem> items, LocalDateTime now) {
        requireUndecided();
        Map<Long, WishRequestLine> byItem = lines.stream()
            .collect(Collectors.toMap(WishRequestLine::getWishItemId, Function.identity()));
        Set<Long> chosen = new HashSet<>();
        for (Approval approval : approvals) {
            if (!byItem.containsKey(approval.wishItemId()) || !chosen.add(approval.wishItemId())
                || approval.quantity() < 1 || approval.quantity() > WishItem.MAX_QUANTITY) {
                throw new WishRuleViolationException(Rule.INVALID_APPROVAL);
            }
        }
        if (chosen.isEmpty()) {
            throw new WishRuleViolationException(Rule.INVALID_APPROVAL);
        }
        approvals.forEach(approval -> byItem.get(approval.wishItemId()).approve(approval.quantity()));
        this.status = Status.APPROVED;
        this.checkout = checkout;
        decide(deciderId, now);
        for (WishItem item : items) {
            if (checkout == Checkout.CART || !chosen.contains(item.getId())) {
                item.unlockFrom(id);
            }
        }
        return approvedLines();
    }

    /**
     * 바로 결제로 만든 주문을 기록한다.
     */
    public void recordOrder(Long orderId) {
        this.orderId = orderId;
    }

    /**
     * @param items 이 조르기의 줄(잠금을 풀 대상)
     */
    public void reject(RejectReason reason, Long deciderId, List<WishItem> items, LocalDateTime now) {
        requireUndecided();
        this.status = Status.REJECTED;
        this.rejectReason = reason;
        decide(deciderId, now);
        items.forEach(item -> item.unlockFrom(id));
    }

    /**
     * 보낸 사람이 처리 전에 철회한다(#968). 누가 철회할 수 있는지는 app의 정책이 먼저 확인한다.
     * 처리된 조르기(승인 · 거절 · 철회)면 WISH_REQUEST_ALREADY_DECIDED.
     *
     * @param items 이 조르기의 줄(잠금을 풀 대상, 줄은 남는다)
     */
    public void cancel(List<WishItem> items, LocalDateTime now) {
        requireUndecided();
        this.status = Status.CANCELLED;
        this.cancelledAt = now;
        items.forEach(item -> item.unlockFrom(id));
    }

    /**
     * 이 사람이 보낸 조르기인지(종류 · ID가 같다).
     */
    public boolean isRequestedBy(AddedBy type, Long actorId) {
        return requestedByType == type && requestedById.equals(actorId);
    }

    /**
     * 바로 결제한 주문의 결제 결과를 반영한다. 결제된 적이 있으면(결제 뒤 취소 포함) PAID: 승인한 줄을 공용 장바구니에서 뺀다.
     * 결제 없이 취소됐으면 CANCELLED: 승인한 줄의 잠금만 푼다. 아직 결제대기면 그대로 둔다.
     *
     * @param items 이 조르기의 줄
     * @return 이번에 정해진 결과. 결과를 기다리는 조르기가 아니거나 아직 결제대기면 빈 값
     */
    public Optional<OrderResult> settleOrder(boolean paid, boolean cancelled, List<WishItem> items) {
        if (!isAwaitingOrder() || (!paid && !cancelled)) {
            return Optional.empty();
        }
        this.orderResult = paid ? OrderResult.PAID : OrderResult.CANCELLED;
        for (WishItem item : items) {
            if (paid) {
                item.purchaseBy(id);
            } else {
                item.unlockFrom(id);
            }
        }
        return Optional.of(orderResult);
    }

    /**
     * 바로 결제로 만든 주문의 결과를 기다리는 중인지.
     */
    public boolean isAwaitingOrder() {
        return status == Status.APPROVED && checkout == Checkout.ORDER && orderId != null && orderResult == null;
    }

    public List<WishRequestLine> approvedLines() {
        return lines.stream().filter(WishRequestLine::isApproved).toList();
    }

    public List<Long> wishItemIds() {
        return lines.stream().map(WishRequestLine::getWishItemId).toList();
    }

    /**
     * 요청 시각(만든 시각).
     */
    public LocalDateTime getRequestedAt() {
        return getCreatedAt();
    }

    private void requireUndecided() {
        if (status != Status.REQUESTED) {
            throw new WishRuleViolationException(Rule.WISH_REQUEST_ALREADY_DECIDED);
        }
    }

    private void decide(Long deciderId, LocalDateTime now) {
        this.decidedById = deciderId;
        this.decidedAt = now;
    }
}
