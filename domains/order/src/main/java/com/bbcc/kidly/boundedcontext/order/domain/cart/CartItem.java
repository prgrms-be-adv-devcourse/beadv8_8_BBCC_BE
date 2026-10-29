package com.bbcc.kidly.boundedcontext.order.domain.cart;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
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
public class CartItem extends OrderTimeEntity {

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
        requirePositive(quantity);
        CartItem item = new CartItem();
        item.memberId = memberId;
        item.optionId = optionId;
        item.childId = childId;
        item.quantity = quantity;
        return item;
    }

    public void addQuantity(int quantity) {
        int added = this.quantity + quantity;
        requirePositive(added);
        this.quantity = added;
    }

    public void changeQuantity(int quantity) {
        requirePositive(quantity);
        this.quantity = quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
        }
    }
}
