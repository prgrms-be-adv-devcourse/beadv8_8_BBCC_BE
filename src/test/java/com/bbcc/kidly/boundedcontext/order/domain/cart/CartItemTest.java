package com.bbcc.kidly.boundedcontext.order.domain.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 장바구니 줄 규칙(주문 정리 5.3). 자녀 소유 · 삭제 확인은 app이 복사본으로 하고(없으면 404), 여기서는 ID만 받는다.
 */
class CartItemTest {

    private static final Long BUYER_ID = 1L;
    private static final Long OPTION_ID = 1001L;
    private static final Long CHILD_ID = 100L;

    @Test
    @DisplayName("담으면 회원 · 옵션 · 자녀 · 수량을 저장하고 가격은 저장하지 않는다")
    void create() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 2);

        assertThat(item.getMemberId()).isEqualTo(BUYER_ID);
        assertThat(item.getOptionId()).isEqualTo(1001L);
        assertThat(item.getChildId()).isEqualTo(CHILD_ID);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("수량은 1 이상이어야 한다")
    void requiresPositiveQuantity() {
        assertThatThrownBy(() -> CartItem.create(BUYER_ID, OPTION_ID, null, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("수량 1로 담을 수 있다 (최솟값)")
    void allowsMinimumQuantity() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, null, 1);

        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("수량을 1로 바꿀 수 있다 (최솟값)")
    void changeQuantityToMinimum() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 3);

        item.changeQuantity(1);

        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 줄에 다시 담으면 수량을 더한다")
    void addQuantity() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 2);

        item.addQuantity(3);

        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수량 변경은 덮어쓴다")
    void changeQuantity() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 2);

        item.changeQuantity(1);

        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("수량을 0 이하로 바꿀 수 없다")
    void changeQuantityRequiresPositive() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 2);

        assertThatThrownBy(() -> item.changeQuantity(0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_QUANTITY);
        assertThat(item.getQuantity()).isEqualTo(2);
    }
}
