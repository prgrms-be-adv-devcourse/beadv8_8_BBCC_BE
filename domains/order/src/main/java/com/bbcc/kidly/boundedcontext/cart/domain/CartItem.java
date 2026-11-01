package com.bbcc.kidly.boundedcontext.cart.domain;

import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 줄. (회원, 옵션, 자녀)가 같으면 한 줄이고 가격은 저장하지 않는다. 같은 줄 찾기는 app(DB 조회)이 한다.
 */
@Entity
@Table(schema = "orders", name = "cart_item", indexes = @Index(
    name = "uk_cart_item_member_option_child",
    columnList = "member_id, option_id, child_id",
    unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends CartTimeEntity {

    /**
     * 한 줄 수량 상한(담기 · 같은 줄 더하기 · 수량 변경 · 주문하기 공통, #253 · #642). 요청 DTO의 @Max(99)와 같은 값.
     */
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long optionId;

    private Long childId;

    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    public static CartItem create(Long memberId, Long optionId, Long childId, int quantity) {
        requireAllowed(quantity);
        CartItem item = new CartItem();
        item.memberId = memberId;
        item.optionId = optionId;
        item.childId = childId;
        item.quantity = quantity;
        return item;
    }

    public void addQuantity(int quantity) {
        int added = this.quantity + quantity;
        requireAllowed(added);
        this.quantity = added;
    }

    public void changeQuantity(int quantity) {
        requireAllowed(quantity);
        this.quantity = quantity;
    }

    private static void requireAllowed(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new CartRuleViolationException(Rule.INVALID_QUANTITY);
        }
    }
}
