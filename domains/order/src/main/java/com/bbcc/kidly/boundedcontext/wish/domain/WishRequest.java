package com.bbcc.kidly.boundedcontext.wish.domain;

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
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매 요청 한 건(#999). 지금 종류는 조르기(WISH) 하나다: 자녀가 자기 장바구니 칸을 골라 보호자에게 사 달라고 한다.
 * 승인 단계는 없다(승인 = 결제): 보호자가 예약된 칸으로 주문해 결제가 끝나면 결제 완료가 된다.
 * 사람은 고정하지 않고 종류 + ID로 둔다(보낸 사람 · 결제한 사람 · 거절한 사람). 받는 사람은 자녀 프로필이다.
 * <ul>
 *   <li>REQUESTED: 든 칸이 장바구니에 예약돼 있다(예약 ID = {@link #reservationId()}). 주인은 그 칸을 빼거나 바꿀 수 없다</li>
 *   <li>PAID: 예약 칸 중 하나 이상이 결제돼 장바구니에서 빠졌다. 결제한 사람 · 시각을 남긴다</li>
 *   <li>REJECTED: 승인자가 거절했다(사유 하나)</li>
 *   <li>CANCELLED: 보낸 사람이 철회했거나(DIRECT, #968) 자녀 · 계정 삭제(#1001) · 시스템(데이터 옮기기)으로 철회됐다</li>
 *   <li>EXPIRED: 기한(보낸 시각 + 설정 기한, 저장하지 않음) 안에 결제 · 거절이 없어 시스템이 만료했다(#1008)</li>
 * </ul>
 * 결제 완료 · 거절 · 철회 · 만료는 REQUESTED에서만 되고, 동시에 오면 @Version으로 먼저 저장한 쪽만 남는다.
 */
@Entity
@Table(schema = "orders", name = "wish_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishRequest extends WishTimeEntity {

    /**
     * 장바구니 예약 ID 앞자리. 장바구니 구매 이벤트의 예약 ID가 이것으로 시작하면 조르기의 예약이다.
     */
    public static final String RESERVATION_PREFIX = "wish-";

    public enum Type {
        WISH
    }

    public enum Status {
        REQUESTED,
        PAID,
        REJECTED,
        CANCELLED,
        EXPIRED
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
     * 철회 원인: 보낸 사람이 직접 · 자녀 삭제 · 자녀 계정 삭제(#1001) · 시스템(#999 데이터 옮기기 등).
     */
    public enum CancelReason {
        DIRECT,
        CHILD_DELETED,
        ACCOUNT_DELETED,
        SYSTEM
    }

    /**
     * 보낼 칸 하나(보낸 때 값).
     *
     * @param productId 상품 복사본이 없으면 null
     */
    public record LineValue(Long cartItemId, Long productId, Long optionId, int quantity) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    /**
     * 받는 사람(자녀 프로필 ID).
     */
    @Column(nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrincipalType requestedByType;

    @Column(nullable = false)
    private Long requestedById;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PrincipalType payerType;

    private Long payerId;

    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RejectReason rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PrincipalType rejectedByType;

    private Long rejectedById;

    private LocalDateTime rejectedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CancelReason cancelReason;

    private LocalDateTime cancelledAt;

    /**
     * 만료 처리 시각(#1008). 만료한 주체는 시스템이다.
     */
    private LocalDateTime expiredAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WishRequestLine> lines = new ArrayList<>();

    /**
     * 조르기를 만든다(칸 없이). 예약 ID가 조르기 ID라 저장해 ID가 생긴 뒤 칸을 예약하고 {@link #addLines}로 담는다.
     *
     * @param message 앞뒤 공백을 지운 메시지. 비었으면 null로 둔다
     * @param maxMessageLength 메시지 최대 길이(정책)
     */
    public static WishRequest send(PrincipalType requestedByType, Long requestedById, Long childId, String message,
        int maxMessageLength) {
        String trimmed = message == null || message.isBlank() ? null : message.strip();
        if (trimmed != null && trimmed.length() > maxMessageLength) {
            throw new WishRuleViolationException(Rule.INVALID_WISH_REQUEST);
        }
        WishRequest request = new WishRequest();
        request.type = Type.WISH;
        request.childId = childId;
        request.requestedByType = requestedByType;
        request.requestedById = requestedById;
        request.message = trimmed;
        request.status = Status.REQUESTED;
        return request;
    }

    /**
     * 예약한 칸을 담는다. 한 번만, 칸이 하나 이상이고 겹치지 않아야 한다.
     */
    public void addLines(List<LineValue> values) {
        Set<Long> seen = new HashSet<>();
        if (!lines.isEmpty() || values.isEmpty()
            || values.stream().anyMatch(value -> value.cartItemId() == null || !seen.add(value.cartItemId()))) {
            throw new WishRuleViolationException(Rule.INVALID_WISH_REQUEST);
        }
        values.forEach(value -> lines.add(new WishRequestLine(this, value.cartItemId(), value.productId(),
            value.optionId(), value.quantity())));
    }

    /**
     * 장바구니 예약 ID("wish-{조르기 ID}"). 저장 뒤에만 부른다.
     */
    public String reservationId() {
        return RESERVATION_PREFIX + id;
    }

    /**
     * 예약 ID에서 조르기 ID를 꺼낸다. 조르기의 예약이 아니면(앞자리가 다르거나 숫자가 아님) 빈 값.
     */
    public static Optional<Long> idOfReservation(String reservationId) {
        if (reservationId == null || !reservationId.startsWith(RESERVATION_PREFIX)) {
            return Optional.empty();
        }
        String digits = reservationId.substring(RESERVATION_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(digits));
    }

    /**
     * 이 조르기에 든 칸인지.
     */
    public boolean containsCartItem(Long cartItemId) {
        return cartItemId != null && lines.stream().anyMatch(line -> cartItemId.equals(line.getCartItemId()));
    }

    /**
     * 예약 칸이 결제됐다: 처음이면 결제 완료로 바꾸고 결제한 사람을 남긴다.
     *
     * @return 이번에 결제 완료가 됐으면 true. 이미 결제 완료면 false
     * @throws WishRuleViolationException 거절 · 철회된 조르기면 WISH_REQUEST_ALREADY_DECIDED
     */
    public boolean pay(PrincipalType payerType, Long payerId, LocalDateTime now) {
        if (status == Status.PAID) {
            return false;
        }
        requireRequested();
        this.status = Status.PAID;
        this.payerType = payerType;
        this.payerId = payerId;
        this.paidAt = now;
        return true;
    }

    public void reject(RejectReason reason, PrincipalType rejectedByType, Long rejectedById, LocalDateTime now) {
        requireRequested();
        this.status = Status.REJECTED;
        this.rejectReason = reason;
        this.rejectedByType = rejectedByType;
        this.rejectedById = rejectedById;
        this.rejectedAt = now;
    }

    /**
     * 철회한다. 누가 철회할 수 있는지는 app의 정책이 먼저 확인한다(직접 철회는 보낸 사람만, #968).
     */
    public void cancel(CancelReason reason, LocalDateTime now) {
        requireRequested();
        this.status = Status.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = now;
    }

    /**
     * 기한이 지나 만료한다(#1008, 시스템). 기한이 지났는지는 app이 설정 기한으로 판단한다.
     */
    public void expire(LocalDateTime now) {
        requireRequested();
        this.status = Status.EXPIRED;
        this.expiredAt = now;
    }

    /**
     * 이 사람이 보낸 조르기인지(종류 · ID가 같다).
     */
    public boolean isRequestedBy(PrincipalType type, Long actorId) {
        return requestedByType == type && requestedById.equals(actorId);
    }

    public List<Long> cartItemIds() {
        return lines.stream().map(WishRequestLine::getCartItemId).toList();
    }

    /**
     * 요청 시각(만든 시각).
     */
    public LocalDateTime getRequestedAt() {
        return getCreatedAt();
    }

    private void requireRequested() {
        if (status != Status.REQUESTED) {
            throw new WishRuleViolationException(Rule.WISH_REQUEST_ALREADY_DECIDED);
        }
    }
}
