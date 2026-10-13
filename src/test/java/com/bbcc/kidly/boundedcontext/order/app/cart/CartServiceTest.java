package com.bbcc.kidly.boundedcontext.order.app.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.cart.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.order.app.cart.dto.CartView;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long OPTION_ID = 1L;
    private static final Long CHILD_ID = 1L;
    private static final Long CART_ITEM_ID = 10L;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductReplicaRepository productReplicaRepository;

    @Mock
    private ChildReplicaRepository childReplicaRepository;

    @InjectMocks
    private CartService cartService;

    @Captor
    private ArgumentCaptor<CartItem> saved;

    // ── 담기 ──

    @Test
    @DisplayName("담기: 새 줄이면 저장하고 created true와 지금 가격의 줄을 돌려준다")
    void addCreatesLine() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddCartItemResult result = cartService.addItem(BUYER_ID, 1L, 1L, 2);

        verify(cartItemRepository).save(saved.capture());
        assertThat(saved.getValue().getMemberId()).isEqualTo(BUYER_ID);
        assertThat(saved.getValue().getOptionId()).isEqualTo(OPTION_ID);
        assertThat(saved.getValue().getChildId()).isEqualTo(CHILD_ID);
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
        assertThat(result.created()).isTrue();
        CartView.Line line = result.line();
        assertThat(line.optionId()).isEqualTo(OPTION_ID);
        assertThat(line.productId()).isEqualTo(1L);
        assertThat(line.productName()).isEqualTo("상품 A");
        assertThat(line.size()).isEqualTo("110");
        assertThat(line.childId()).isEqualTo(CHILD_ID);
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.unitPrice()).isEqualTo(25_000L);
        assertThat(line.purchasable()).isTrue();
        assertThat(line.lineAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("담기: 자녀를 고르지 않으면 자녀 없이 같은 줄을 찾고 자녀 없는 줄로 저장한다")
    void addWithoutChild() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddCartItemResult result = cartService.addItem(BUYER_ID, 1L, null, 1);

        verify(cartItemRepository).findByMemberIdAndOptionIdAndChildId(BUYER_ID, OPTION_ID, null);
        verify(cartItemRepository).save(saved.capture());
        assertThat(saved.getValue().getChildId()).isNull();
        assertThat(result.line().childId()).isNull();
        verify(childReplicaRepository, never()).findByChildId(any());
    }

    @Test
    @DisplayName("담기: 같은 줄(회원, 옵션, 자녀)이 있으면 수량을 더하고 created false")
    void addMergesSameLine() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));
        CartItem existing = CartItem.create(BUYER_ID, OPTION_ID, CHILD_ID, 1);
        when(cartItemRepository.findByMemberIdAndOptionIdAndChildId(BUYER_ID, OPTION_ID, CHILD_ID))
            .thenReturn(Optional.of(existing));

        AddCartItemResult result = cartService.addItem(BUYER_ID, 1L, 1L, 2);

        assertThat(existing.getQuantity()).isEqualTo(3);
        assertThat(result.created()).isFalse();
        assertThat(result.line().quantity()).isEqualTo(3);
        assertThat(result.line().lineAmount()).isEqualTo(75_000L);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 상품 옵션 복사본이 없으면 PRODUCT_OPTION_NOT_FOUND")
    void addFailsWhenOptionMissing() {
        assertAddFails(BUYER_ID, 1L, 1L, 0, OrderErrorCode.PRODUCT_OPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 판매자 본인 상품이면 판매 상태보다 먼저 OWN_PRODUCT")
    void addFailsWhenOwnProduct() {
        givenProduct(product(OPTION_ID, 25_000L, "STOPPED", false));

        assertAddFails(SELLER_ID, 1L, 1L, 0, OrderErrorCode.OWN_PRODUCT);
    }

    @Test
    @DisplayName("담기: 판매 중이 아니거나 품절이면 자녀 확인보다 먼저 PRODUCT_NOT_PURCHASABLE")
    void addFailsWhenNotPurchasable() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", true));

        assertAddFails(BUYER_ID, 1L, 1L, 0, OrderErrorCode.PRODUCT_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("담기: 자녀 복사본이 없으면 수량 확인보다 먼저 CHILD_NOT_FOUND")
    void addFailsWhenChildMissing() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));

        assertAddFails(BUYER_ID, 1L, 1L, 0, OrderErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 다른 보호자의 자녀면 CHILD_NOT_FOUND")
    void addFailsWhenOthersChild() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(OTHER_ID, false));

        assertAddFails(BUYER_ID, 1L, 1L, 1, OrderErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 삭제된 자녀면 CHILD_NOT_FOUND")
    void addFailsWhenChildDeleted() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, true));

        assertAddFails(BUYER_ID, 1L, 1L, 1, OrderErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 수량이 1보다 작으면 INVALID_QUANTITY")
    void addFailsWhenQuantityInvalid() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));

        assertAddFails(BUYER_ID, 1L, 1L, 0, OrderErrorCode.INVALID_QUANTITY);
    }

    // ── 조회 ──

    @Test
    @DisplayName("조회: 저장소 순서(최근 담은 줄부터)대로 줄을 만들고, 합계는 구매 가능한 줄만 더한다")
    void getCart() {
        when(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).thenReturn(List.of(
            CartItem.create(BUYER_ID, 2L, null, 1),
            CartItem.create(BUYER_ID, 1L, CHILD_ID, 2)));
        when(productReplicaRepository.findAllByOptionIdIn(any())).thenReturn(List.of(
            product(1L, 25_000L, "ON_SALE", false),
            product(2L, 30_000L, "ON_SALE", true)));

        CartView cart = cartService.getCart(BUYER_ID);

        assertThat(cart.items()).hasSize(2);
        CartView.Line soldOut = cart.items().get(0);
        assertThat(soldOut.optionId()).isEqualTo(2L);
        assertThat(soldOut.purchasable()).isFalse();
        assertThat(soldOut.unitPrice()).isEqualTo(30_000L);
        assertThat(soldOut.lineAmount()).isEqualTo(30_000L);
        assertThat(cart.items().get(1).lineAmount()).isEqualTo(50_000L);
        assertThat(cart.totalAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("조회: 상품 복사본이 없는 줄도 보여주되 구매 불가 · 상품 필드 null · 금액 0")
    void getCartWithoutReplica() {
        when(cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID)).thenReturn(List.of(
            CartItem.create(BUYER_ID, 9L, null, 2)));

        CartView cart = cartService.getCart(BUYER_ID);

        assertThat(cart.items()).hasSize(1);
        CartView.Line line = cart.items().get(0);
        assertThat(line.optionId()).isEqualTo(9L);
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.productId()).isNull();
        assertThat(line.productName()).isNull();
        assertThat(line.size()).isNull();
        assertThat(line.unitPrice()).isNull();
        assertThat(line.purchasable()).isFalse();
        assertThat(line.lineAmount()).isEqualTo(0L);
        assertThat(cart.totalAmount()).isEqualTo(0L);
    }

    // ── 수량 변경 ──

    @Test
    @DisplayName("수량 변경: 수량을 덮어쓰고 지금 가격의 줄을 돌려준다")
    void changeQuantity() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findByIdAndMemberId(CART_ITEM_ID, BUYER_ID)).thenReturn(Optional.of(item));
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));

        CartView.Line line = cartService.changeQuantity(BUYER_ID, CART_ITEM_ID, 4);

        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(line.quantity()).isEqualTo(4);
        assertThat(line.lineAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("수량 변경: 없거나 다른 회원의 줄이면 수량 확인보다 먼저 CART_ITEM_NOT_FOUND")
    void changeQuantityFailsWhenLineMissing() {
        assertThatThrownBy(
            () -> cartService.changeQuantity(BUYER_ID, CART_ITEM_ID, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수량 변경: 수량이 1보다 작으면 INVALID_QUANTITY")
    void changeQuantityFailsWhenQuantityInvalid() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findByIdAndMemberId(CART_ITEM_ID, BUYER_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(
            () -> cartService.changeQuantity(BUYER_ID, CART_ITEM_ID, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_QUANTITY);
    }

    // ── 삭제 ──

    @Test
    @DisplayName("삭제: 본인 줄이면 지운다")
    void removeItem() {
        CartItem item = CartItem.create(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findByIdAndMemberId(CART_ITEM_ID, BUYER_ID)).thenReturn(Optional.of(item));

        cartService.removeItem(BUYER_ID, CART_ITEM_ID);

        verify(cartItemRepository).delete(item);
    }

    @Test
    @DisplayName("삭제: 없거나 다른 회원의 줄이면 CART_ITEM_NOT_FOUND")
    void removeItemFailsWhenLineMissing() {
        assertThatThrownBy(() -> cartService.removeItem(BUYER_ID, CART_ITEM_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.CART_ITEM_NOT_FOUND);
        verify(cartItemRepository, never()).delete(any());
    }

    private void assertAddFails(Long memberId, Long optionId, Long childId, int quantity,
        OrderErrorCode expected) {
        assertThatThrownBy(() -> cartService.addItem(memberId, optionId, childId, quantity))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(expected);
        verify(cartItemRepository, never()).save(any());
    }

    private void givenProduct(ReplicaProduct product) {
        when(productReplicaRepository.findByOptionId(product.getOptionId())).thenReturn(Optional.of(product));
    }

    private void givenChild(ReplicaChild child) {
        when(childReplicaRepository.findByChildId(CHILD_ID)).thenReturn(Optional.of(child));
    }

    private static ReplicaProduct product(Long optionId, long unitPrice, String status, boolean soldOut) {
        return ReplicaProduct.create(new ReplicaProduct.Values(optionId, 1L, SELLER_ID, "상품 A", "110", unitPrice,
            1L, status, soldOut, OCCURRED_AT));
    }

    private static ReplicaChild child(Long memberId, boolean deleted) {
        return ReplicaChild.create(new ReplicaChild.Values(CHILD_ID, memberId, null, new BigDecimal("115.0"),
            new BigDecimal("20.0"), "NORMAL", deleted, OCCURRED_AT));
    }
}
