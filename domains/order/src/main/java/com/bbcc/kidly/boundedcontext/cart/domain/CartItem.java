package com.bbcc.kidly.boundedcontext.cart.domain;

import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 줄. 주인(#998: 회원 또는 자녀) 한 명의 칸이고, (주인, 옵션, 입을 자녀)가 같으면 한 줄이다. 가격은 저장하지 않는다.
 * memberId는 결제할 보호자 회원이다(회원 주인이면 그 회원, 자녀 주인이면 보호자). 같은 줄 찾기는 app(DB 조회)이 한다.
 * 다른 영역이 칸을 예약하면(reservationId, #998) 주인은 그 칸을 빼거나 수량을 바꿀 수 없다.
 */
@Entity
@Table(schema = "orders", name = "cart_item",
    uniqueConstraints = @UniqueConstraint(name = CartItem.OWNER_LINE_UNIQUE,
        columnNames = {"owner_type", "owner_id", "option_id", "child_id"}),
    indexes = @Index(name = "idx_cart_item_reservation", columnList = "reservation_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends CartTimeEntity {

    public static final String OWNER_LINE_UNIQUE = "uk_cart_item_owner_option_child";

    /**
     * 한 줄 수량 상한(담기 · 같은 줄 더하기 · 수량 변경 · 주문하기 공통, #253 · #642). 요청 DTO의 @Max(99)와 같은 값.
     */
    public static final int MAX_QUANTITY = 99;

    /**
     * 장바구니 주인 종류(#998).
     */
    public enum OwnerType {
        MEMBER,
        CHILD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnerType ownerType;

    /**
     * 회원 주인이면 회원 ID, 자녀 주인이면 자녀 프로필 ID.
     */
    @Column(nullable = false)
    private Long ownerId;

    /**
     * 결제할 보호자 회원 ID(회원 주인이면 ownerId와 같다).
     */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long optionId;

    private Long childId;

    @Column(nullable = false)
    private int quantity;

    /**
     * 칸을 예약한 쪽이 준 불투명 예약 ID. 예약이 없으면 null.
     */
    @Column(length = 64)
    private String reservationId;

    @Version
    private Long version;

    /**
     * @param memberId 결제할 보호자 회원 ID
     */
    public static CartItem create(OwnerType ownerType, Long ownerId, Long memberId, Long optionId, Long childId,
        int quantity) {
        requireAllowed(quantity);
        CartItem item = new CartItem();
        item.ownerType = Objects.requireNonNull(ownerType);
        item.ownerId = Objects.requireNonNull(ownerId);
        item.memberId = Objects.requireNonNull(memberId);
        item.optionId = optionId;
        item.childId = childId;
        item.quantity = quantity;
        return item;
    }

    public boolean isOwnedBy(OwnerType type, Long id) {
        return ownerType == type && ownerId.equals(id);
    }

    public boolean isReserved() {
        return reservationId != null;
    }

    public void addQuantity(int quantity) {
        requireNotReserved();
        int added = this.quantity + quantity;
        requireAllowed(added);
        this.quantity = added;
    }

    public void changeQuantity(int quantity) {
        requireNotReserved();
        requireAllowed(quantity);
        this.quantity = quantity;
    }

    /**
     * 주인이 빼기 전에 확인한다. 예약된 칸은 뺄 수 없다.
     */
    public void requireRemovable() {
        requireNotReserved();
    }

    /**
     * 예약한다. 같은 예약 ID면 그대로, 다른 예약이 있으면 거절한다.
     */
    public void reserve(String reservationId) {
        if (this.reservationId != null && !this.reservationId.equals(reservationId)) {
            throw new CartRuleViolationException(Rule.CART_ITEM_RESERVED);
        }
        this.reservationId = reservationId;
    }

    public void release() {
        this.reservationId = null;
    }

    private void requireNotReserved() {
        if (reservationId != null) {
            throw new CartRuleViolationException(Rule.CART_ITEM_RESERVED);
        }
    }

    private static void requireAllowed(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new CartRuleViolationException(Rule.INVALID_QUANTITY);
        }
    }
}
