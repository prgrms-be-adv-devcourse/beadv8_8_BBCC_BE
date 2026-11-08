package com.bbcc.kidly.boundedcontext.cart.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartReservationRepository;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCheckout;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import com.bbcc.kidly.boundedcontext.cart.domain.CartReservation;
import com.bbcc.kidly.boundedcontext.cart.out.order.CartCatalogAdapter;
import com.bbcc.kidly.boundedcontext.cart.out.order.CartEventsAdapter;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderEventOutbox;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.provided.ProvidedEventService;
import com.bbcc.kidly.boundedcontext.order.app.provided.ProvidedReplicaService;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private CartReservationRepository reservationRepository;

    @Mock
    private SellerReplicaRepository sellerReplicaRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private OrderEventOutbox outbox;

    // 주문 컨텍스트는 어댑터 + 실제 들어오는 포트 구현에 위 저장소 mock을 넣어 쓴다(#617 · #641: 장바구니 동작은 그대로)
    private CartService cartService;

    @Captor
    private ArgumentCaptor<CartItem> saved;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository, reservationRepository,
            new CartCatalogAdapter(
                // 자녀 계정 복사본(#906)은 장바구니가 쓰지 않으므로 없이(null) 둔다
                new ProvidedReplicaService(productReplicaRepository, sellerReplicaRepository, childReplicaRepository,
                    null)),
            new CartEventsAdapter(new ProvidedEventService(eventPublisher, outbox)));
    }

    // ── 담기 ──

    @Test
    @DisplayName("담기: 새 줄이면 저장하고 created true와 지금 가격의 줄을 돌려준다")
    void addCreatesLine() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddCartItemResult result = cartService.addItem(member(BUYER_ID), 1L, 1L, 2);

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

        AddCartItemResult result = cartService.addItem(member(BUYER_ID), 1L, null, 1);

        verify(cartItemRepository).findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null);
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
        CartItem existing = line(BUYER_ID, OPTION_ID, CHILD_ID, 1);
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, CHILD_ID))
            .thenReturn(Optional.of(existing));

        AddCartItemResult result = cartService.addItem(member(BUYER_ID), 1L, 1L, 2);

        assertThat(existing.getQuantity()).isEqualTo(3);
        assertThat(result.created()).isFalse();
        assertThat(result.line().quantity()).isEqualTo(3);
        assertThat(result.line().lineAmount()).isEqualTo(75_000L);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 같은 줄 합산이 정확히 99면 담기고, 99를 1 넘으면 INVALID_QUANTITY이며 줄 수량은 그대로")
    void addMergeUpToMaxQuantity() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        CartItem existing = line(BUYER_ID, OPTION_ID, null, 98);
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null))
            .thenReturn(Optional.of(existing));

        assertThat(cartService.addItem(member(BUYER_ID), OPTION_ID, null, 1).line().quantity()).isEqualTo(99);

        assertThatThrownBy(() -> cartService.addItem(member(BUYER_ID), OPTION_ID, null, 1))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_QUANTITY);
        assertThat(existing.getQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("담기: 같은 줄에 더한 수량이 1보다 작아지면 INVALID_QUANTITY이며 줄 수량은 그대로")
    void addMergeFailsWhenQuantityInvalid() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        CartItem existing = line(BUYER_ID, OPTION_ID, null, 2);
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addItem(member(BUYER_ID), OPTION_ID, null, -5))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_QUANTITY);
        assertThat(existing.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("담기: 자녀 없는 같은 줄이 있으면 수량을 더하고 created false")
    void addMergesSameLineWithoutChild() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        CartItem existing = line(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null))
            .thenReturn(Optional.of(existing));

        AddCartItemResult result = cartService.addItem(member(BUYER_ID), OPTION_ID, null, 2);

        assertThat(existing.getQuantity()).isEqualTo(3);
        assertThat(result.created()).isFalse();
        assertThat(result.line().childId()).isNull();
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("담기: 상품 옵션 복사본이 없으면 PRODUCT_OPTION_NOT_FOUND")
    void addFailsWhenOptionMissing() {
        assertAddFails(BUYER_ID, 1L, 1L, 0, CartErrorCode.PRODUCT_OPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 판매자 본인 상품이면 판매 상태보다 먼저 OWN_PRODUCT")
    void addFailsWhenOwnProduct() {
        givenProduct(product(OPTION_ID, 25_000L, "STOPPED", false));

        assertAddFails(SELLER_ID, 1L, 1L, 0, CartErrorCode.OWN_PRODUCT);
    }

    @Test
    @DisplayName("담기: 판매 중이 아니거나 품절이면 자녀 확인보다 먼저 PRODUCT_NOT_PURCHASABLE")
    void addFailsWhenNotPurchasable() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", true));

        assertAddFails(BUYER_ID, 1L, 1L, 0, CartErrorCode.PRODUCT_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("담기: 자녀 복사본이 없으면 수량 확인보다 먼저 CHILD_NOT_FOUND")
    void addFailsWhenChildMissing() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));

        assertAddFails(BUYER_ID, 1L, 1L, 0, CartErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 다른 보호자의 자녀면 CHILD_NOT_FOUND")
    void addFailsWhenOthersChild() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(OTHER_ID, false));

        assertAddFails(BUYER_ID, 1L, 1L, 1, CartErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 삭제된 자녀면 CHILD_NOT_FOUND")
    void addFailsWhenChildDeleted() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, true));

        assertAddFails(BUYER_ID, 1L, 1L, 1, CartErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    @DisplayName("담기: 수량이 1보다 작으면 INVALID_QUANTITY")
    void addFailsWhenQuantityInvalid() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));

        assertAddFails(BUYER_ID, 1L, 1L, 0, CartErrorCode.INVALID_QUANTITY);
    }

    // ── 담기 이벤트(#654) ──

    @Test
    @DisplayName("담기 이벤트: 새 줄이면 cart.item-added를 Spring 이벤트 · 아웃박스로 한 번씩, 키는 회원 ID · 대상은 장바구니 줄 ID")
    void addPublishesItemAddedForNewLine() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        givenChild(child(BUYER_ID, false));
        when(cartItemRepository.save(any())).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", CART_ITEM_ID);
            return item;
        });

        cartService.addItem(member(BUYER_ID), OPTION_ID, CHILD_ID, 2);

        CartItemAddedEvent event = publishedEvent();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.eventType()).isEqualTo("cart.item-added");
        assertThat(event.producer()).isEqualTo("order");
        assertThat(event.aggregateId()).isEqualTo(String.valueOf(CART_ITEM_ID));
        assertThat(event.correlationId()).isEqualTo(String.valueOf(BUYER_ID));
        assertThat(event.data()).isEqualTo(new CartItemAddedEvent.Data(CART_ITEM_ID, BUYER_ID, CHILD_ID, 1L,
            OPTION_ID, 2, 2, "MEMBER", BUYER_ID));
    }

    @Test
    @DisplayName("담기 이벤트: 같은 줄에 합치면 quantity는 이번에 담은 수량, cartQuantity는 담은 뒤 줄 수량")
    void addPublishesItemAddedForMergedLine() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        CartItem existing = line(BUYER_ID, OPTION_ID, null, 3);
        ReflectionTestUtils.setField(existing, "id", CART_ITEM_ID);
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null))
            .thenReturn(Optional.of(existing));

        cartService.addItem(member(BUYER_ID), OPTION_ID, null, 2);

        assertThat(publishedEvent().data()).isEqualTo(new CartItemAddedEvent.Data(CART_ITEM_ID, BUYER_ID, null, 1L,
            OPTION_ID, 2, 5, "MEMBER", BUYER_ID));
    }

    @Test
    @DisplayName("담기 이벤트: 같은 줄 합산이 99를 넘어 실패하면 내지 않는다")
    void addMergeFailurePublishesNothing() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER,
            BUYER_ID, OPTION_ID, null))
            .thenReturn(Optional.of(line(BUYER_ID, OPTION_ID, null, 99)));

        assertThatThrownBy(() -> cartService.addItem(member(BUYER_ID), OPTION_ID, null, 1))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_QUANTITY);
        verifyNoEvent();
    }

    @Test
    @DisplayName("담기 이벤트: 수량 변경 · 빼기는 담기 이벤트를 내지 않는다(빼기는 cart.item-removed 하나, #709)")
    void changeAndRemovePublishNoItemAdded() {
        CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

        cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 4);
        cartService.removeItem(member(BUYER_ID), CART_ITEM_ID);

        assertThat(publishedDomainEvent()).isInstanceOf(CartItemRemovedEvent.class);
    }

    // ── 조회 ──

    @Test
    @DisplayName("조회: 저장소 순서(최근 담은 줄부터)대로 줄을 만들고 판매자 ID를 붙이며, 합계는 구매 가능한 줄만 더한다")
    void getCart() {
        when(cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.MEMBER,
            BUYER_ID)).thenReturn(List.of(
            line(BUYER_ID, 2L, null, 1),
            line(BUYER_ID, 1L, CHILD_ID, 2)));
        when(productReplicaRepository.findAllByOptionIdIn(any())).thenReturn(List.of(
            product(1L, 25_000L, "ON_SALE", false),
            product(2L, 30_000L, "ON_SALE", true)));

        CartView cart = cartService.getCart(member(BUYER_ID));

        assertThat(cart.items()).hasSize(2);
        CartView.Line soldOut = cart.items().get(0);
        assertThat(soldOut.optionId()).isEqualTo(2L);
        assertThat(soldOut.sellerId()).isEqualTo(SELLER_ID);
        assertThat(soldOut.purchasable()).isFalse();
        assertThat(soldOut.unitPrice()).isEqualTo(30_000L);
        assertThat(soldOut.lineAmount()).isEqualTo(30_000L);
        assertThat(cart.items().get(1).lineAmount()).isEqualTo(50_000L);
        assertThat(cart.totalAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("조회: 상품 복사본이 없는 줄도 보여주되 구매 불가 · 상품 필드(판매자 ID 포함) null · 금액 0, NPE 없음")
    void getCartWithoutReplica() {
        when(cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.MEMBER,
            BUYER_ID)).thenReturn(List.of(
            line(BUYER_ID, 9L, null, 2)));

        CartView cart = cartService.getCart(member(BUYER_ID));

        assertThat(cart.items()).hasSize(1);
        CartView.Line line = cart.items().get(0);
        assertThat(line.optionId()).isEqualTo(9L);
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.productId()).isNull();
        assertThat(line.sellerId()).isNull();
        assertThat(line.productName()).isNull();
        assertThat(line.size()).isNull();
        assertThat(line.unitPrice()).isNull();
        assertThat(line.purchasable()).isFalse();
        assertThat(line.lineAmount()).isEqualTo(0L);
        assertThat(cart.totalAmount()).isEqualTo(0L);
    }

    // ── 수량 변경 ──

    @Test
    @DisplayName("수량 변경: 수량을 덮어쓰고 지금 가격의 줄을 돌려준다(이벤트 없음)")
    void changeQuantity() {
        CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));

        CartView.Line line = cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 4);

        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(line.quantity()).isEqualTo(4);
        assertThat(line.lineAmount()).isEqualTo(100_000L);
        // 수량 변경은 담기 · 빼기 이벤트를 내지 않는다(#709)
        verifyNoEvent();
    }

    @Test
    @DisplayName("수량 변경: 없거나 다른 회원의 줄이면 수량 확인보다 먼저 CART_ITEM_NOT_FOUND")
    void changeQuantityFailsWhenLineMissing() {
        assertThatThrownBy(
            () -> cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수량 변경: 수량이 1보다 작으면 INVALID_QUANTITY")
    void changeQuantityFailsWhenQuantityInvalid() {
        CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(
            () -> cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_QUANTITY);
    }

    // ── 삭제 ──

    @Test
    @DisplayName("삭제: 본인 줄이면 지우고 cart.item-removed를 낸다(뺄 때 줄 수량 · 자녀 · 상품, 키 = 회원 ID, #709)")
    void removeItem() {
        givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
        CartItem item = line(BUYER_ID, OPTION_ID, CHILD_ID, 3);
        when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

        cartService.removeItem(member(BUYER_ID), CART_ITEM_ID);

        verify(cartItemRepository).delete(item);
        CartItemRemovedEvent event = (CartItemRemovedEvent) publishedDomainEvent();
        assertThat(event.data()).isEqualTo(
            new CartItemRemovedEvent.Data(item.getId(), BUYER_ID, CHILD_ID, 1L, OPTION_ID, 3, "MEMBER", BUYER_ID));
        assertThat(event.correlationId()).isEqualTo(String.valueOf(BUYER_ID));
    }

    @Test
    @DisplayName("삭제: 상품 정보가 없어도 빼고, 이벤트의 상품 ID만 null이다")
    void removeItemWithoutProduct() {
        CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
        when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

        cartService.removeItem(member(BUYER_ID), CART_ITEM_ID);

        assertThat(((CartItemRemovedEvent) publishedDomainEvent()).data().productId()).isNull();
    }

    @Test
    @DisplayName("삭제: 없거나 다른 회원의 줄이면 CART_ITEM_NOT_FOUND")
    void removeItemFailsWhenLineMissing() {
        assertThatThrownBy(() -> cartService.removeItem(member(BUYER_ID), CART_ITEM_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
        verify(cartItemRepository, never()).delete(any());
        verifyNoEvent();
    }

    @Nested
    @DisplayName("결제완료 뒤 장바구니 정리(#998: 주문이 지울 칸 정보를 넘긴다)")
    class ClearOrdered {

        @Test
        @DisplayName("칸 ID가 없으면 구매자 회원의 같은 (옵션, 자녀) 칸을 정리한다. 수량을 모르는 예전 줄은 칸 전체를 산 것으로 보고 지운다. "
            + "칸마다 cart.item-purchased(주문 · 주문상품 ID, quantity = 칸 전체)")
        void deletesBuyersMatchingLines() {
            CartItem withChild = line(BUYER_ID, OPTION_ID, CHILD_ID, 5);
            CartItem withoutChild = line(BUYER_ID, 3L, null, 1);
            when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER, BUYER_ID,
                OPTION_ID, CHILD_ID)).thenReturn(Optional.of(withChild));
            when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER, BUYER_ID, 3L,
                null)).thenReturn(Optional.of(withoutChild));
            ReflectionTestUtils.setField(withChild, "id", 11L);
            ReflectionTestUtils.setField(withoutChild, "id", 12L);

            cartService.clearOrdered(7L, List.of(
                new CartCleanup.OrderedLine(71L, null, BUYER_ID, OPTION_ID, CHILD_ID, null),
                new CartCleanup.OrderedLine(72L, null, BUYER_ID, 3L, null, null)));

            verify(cartItemRepository).delete(withChild);
            verify(cartItemRepository).delete(withoutChild);
            List<CartItemPurchasedEvent> events = publishedPurchasedEvents(2);
            assertThat(events).extracting(event -> event.data().orderId()).containsOnly(7L);
            assertThat(events).extracting(event -> event.data().orderItemId()).containsExactly(71L, 72L);
            assertThat(events).extracting(event -> event.data().cartItemId()).containsExactly(11L, 12L);
            assertThat(events).extracting(event -> event.data().quantity()).containsExactly(5, 1);
        }

        @Test
        @DisplayName("칸 ID가 있으면 그 칸을 지운다: 예약 칸을 다 사면 지우고 cart.item-purchased에 예약 ID를 싣는다")
        void deletesReservedLineForAllowedPayer() {
            CartItem childLine = CartItem.create(OwnerType.CHILD, CHILD_ID, BUYER_ID, OPTION_ID, CHILD_ID, 1);
            ReflectionTestUtils.setField(childLine, "id", 21L);
            childLine.reserve("wish-1");
            when(cartItemRepository.findById(21L)).thenReturn(Optional.of(childLine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(71L, 21L, BUYER_ID, OPTION_ID, CHILD_ID, 1)));

            verify(cartItemRepository).delete(childLine);
            CartItemPurchasedEvent event = publishedPurchasedEvents(1).getFirst();
            assertThat(event.data()).isEqualTo(new CartItemPurchasedEvent.Data(21L, "CHILD", CHILD_ID, BUYER_ID,
                OPTION_ID, CHILD_ID, 1, 7L, 71L, "wish-1"));
        }

        @Test
        @DisplayName("결제자가 예약 허용 주체이지만 칸의 결제 회원과 다른 회원이어도(보호자 2명) 칸 ID로 찾은 칸을 정리하고 "
            + "cart.item-purchased를 낸다(memberId · 키는 칸의 결제 회원)")
        void deletesRecordedLineForPayerOtherThanLineMember() {
            CartItem childLine = CartItem.create(OwnerType.CHILD, CHILD_ID, OTHER_ID, OPTION_ID, CHILD_ID, 2);
            ReflectionTestUtils.setField(childLine, "id", 23L);
            childLine.reserve("wish-2");
            when(cartItemRepository.findById(23L)).thenReturn(Optional.of(childLine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(73L, 23L, BUYER_ID, OPTION_ID, CHILD_ID, 2)));

            verify(cartItemRepository).delete(childLine);
            CartItemPurchasedEvent event = publishedPurchasedEvents(1).getFirst();
            assertThat(event.data()).isEqualTo(new CartItemPurchasedEvent.Data(23L, "CHILD", CHILD_ID, OTHER_ID,
                OPTION_ID, CHILD_ID, 2, 7L, 73L, "wish-2"));
        }

        @Test
        @DisplayName("일부만 사면 산 수량만큼 칸 수량이 줄고 칸 · 예약은 남는다. cart.item-purchased의 quantity = 이번에 산 수량")
        void deductsPartiallyPurchasedLine() {
            CartItem childLine = CartItem.create(OwnerType.CHILD, CHILD_ID, BUYER_ID, OPTION_ID, CHILD_ID, 3);
            ReflectionTestUtils.setField(childLine, "id", 24L);
            childLine.reserve("wish-3");
            when(cartItemRepository.findById(24L)).thenReturn(Optional.of(childLine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(74L, 24L, BUYER_ID, OPTION_ID, CHILD_ID, 1)));

            verify(cartItemRepository, never()).delete(any());
            assertThat(childLine.getQuantity()).isEqualTo(2);
            assertThat(childLine.getReservationId()).isEqualTo("wish-3");
            CartItemPurchasedEvent event = publishedPurchasedEvents(1).getFirst();
            assertThat(event.data().quantity()).isEqualTo(1);
            assertThat(event.data().reservationId()).isEqualTo("wish-3");
        }

        @Test
        @DisplayName("칸 ID 없이 수량이 있는 예전 방식 줄은 구매자 회원의 (옵션, 자녀) 칸에서 산 수량만큼 뺀다(칸은 남는다)")
        void deductsBuyersLineWithoutCartItemId() {
            CartItem mine = line(BUYER_ID, OPTION_ID, CHILD_ID, 4);
            ReflectionTestUtils.setField(mine, "id", 26L);
            when(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER, BUYER_ID,
                OPTION_ID, CHILD_ID)).thenReturn(Optional.of(mine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(76L, null, BUYER_ID, OPTION_ID, CHILD_ID, 3)));

            verify(cartItemRepository, never()).delete(any());
            assertThat(mine.getQuantity()).isEqualTo(1);
            CartItemPurchasedEvent event = publishedPurchasedEvents(1).getFirst();
            assertThat(event.data().cartItemId()).isEqualTo(26L);
            assertThat(event.data().quantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("칸 수량보다 많이 샀으면(자기 칸은 수량 제한 없음) 칸을 지우고 quantity = 산 수량")
        void deletesLineWhenPurchasedAll() {
            CartItem mine = line(BUYER_ID, OPTION_ID, null, 2);
            ReflectionTestUtils.setField(mine, "id", 25L);
            when(cartItemRepository.findById(25L)).thenReturn(Optional.of(mine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(75L, 25L, BUYER_ID, OPTION_ID, null, 5)));

            verify(cartItemRepository).delete(mine);
            assertThat(publishedPurchasedEvents(1).getFirst().data().quantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("주문에 기록된 칸은 예약이 먼저 풀렸어도 지운다(예약 ID 없음)")
        void deletesRecordedFamilyLineAfterRelease() {
            CartItem childLine = CartItem.create(OwnerType.CHILD, CHILD_ID, BUYER_ID, OPTION_ID, CHILD_ID, 1);
            ReflectionTestUtils.setField(childLine, "id", 31L);
            when(cartItemRepository.findById(31L)).thenReturn(Optional.of(childLine));

            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(71L, 31L, BUYER_ID, OPTION_ID, CHILD_ID, 1)));

            verify(cartItemRepository).delete(childLine);
            CartItemPurchasedEvent event = publishedPurchasedEvents(1).getFirst();
            assertThat(event.data().orderId()).isEqualTo(7L);
            assertThat(event.data().reservationId()).isNull();
        }

        @Test
        @DisplayName("지울 칸이 이미 없으면 아무것도 하지 않는다(이벤트 없음)")
        void ignoresMissingLines() {
            cartService.clearOrdered(7L,
                List.of(new CartCleanup.OrderedLine(71L, null, BUYER_ID, OPTION_ID, null, 1)));

            verify(cartItemRepository, never()).delete(any());
            verifyNoEvent();
        }

        // 결제 정리는 cart.item-purchased만 낸다(cart.item-removed는 사용자가 뺄 때만): 다른 종류가 섞이면 cast에서 실패한다
        private List<CartItemPurchasedEvent> publishedPurchasedEvents(int count) {
            ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
            verify(eventPublisher, org.mockito.Mockito.times(count)).publish(published.capture());
            return published.getAllValues().stream().map(CartItemPurchasedEvent.class::cast).toList();
        }
    }

    @Nested
    @DisplayName("자녀 장바구니 · 남의 줄(#998)")
    class ChildCart {

        @Test
        @DisplayName("자녀 주인은 입을 아이가 자기로 고정이다: 비우면 자기, 다른 자녀면 400. 칸 주인은 CHILD, member_id는 보호자")
        void childWearerFixed() {
            givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));
            givenChild(child(BUYER_ID, false));
            when(cartItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            cartService.addItem(childActor(), OPTION_ID, null, 1);

            verify(cartItemRepository).save(saved.capture());
            assertThat(saved.getValue().getOwnerType()).isEqualTo(OwnerType.CHILD);
            assertThat(saved.getValue().getOwnerId()).isEqualTo(CHILD_ID);
            assertThat(saved.getValue().getMemberId()).isEqualTo(BUYER_ID);
            assertThat(saved.getValue().getChildId()).isEqualTo(CHILD_ID);
            assertThatThrownBy(() -> cartService.addItem(childActor(), OPTION_ID, CHILD_ID + 1, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CartErrorCode.CHILD_CART_CHILD_FIXED);
        }

        @Test
        @DisplayName("자녀 프로필 복사본이 없거나 삭제됐으면 담을 수 없다(CHILD_NOT_FOUND, CART-C-01)")
        void childProfileRequired() {
            givenProduct(product(OPTION_ID, 25_000L, "ON_SALE", false));

            assertThatThrownBy(() -> cartService.addItem(childActor(), OPTION_ID, null, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CartErrorCode.CHILD_NOT_FOUND);
            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("남의 줄: 같은 가족(보호자 · 자녀 사이)이면 403, 다른 회원이면 404")
        void othersLines() {
            CartItem childLine = CartItem.create(OwnerType.CHILD, CHILD_ID, BUYER_ID, OPTION_ID, CHILD_ID, 1);
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(childLine));

            assertThatThrownBy(() -> cartService.removeItem(member(BUYER_ID), CART_ITEM_ID))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_FORBIDDEN);
            assertThatThrownBy(() -> cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 2))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_FORBIDDEN);
            assertThatThrownBy(() -> cartService.removeItem(member(OTHER_ID), CART_ITEM_ID))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            verify(cartItemRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("칸 예약 · 주문 가능 확인(#998)")
    class Reservation {

        @Test
        @DisplayName("예약하면 칸에 예약 ID, 허용 주체를 저장한다. 예약된 칸은 주인이 빼거나 수량을 바꾸면 409")
        void reserveLocksLine() {
            CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
            when(cartItemRepository.findAllByIdIn(any())).thenReturn(List.of(item));
            when(cartItemRepository.findById(CART_ITEM_ID)).thenReturn(Optional.of(item));

            cartService.reserve(List.of(CART_ITEM_ID), List.of(new CartReservations.Payer("MEMBER", OTHER_ID)), "r-1");

            assertThat(item.getReservationId()).isEqualTo("r-1");
            ArgumentCaptor<CartReservation> payer = ArgumentCaptor.forClass(CartReservation.class);
            verify(reservationRepository).save(payer.capture());
            assertThat(payer.getValue().allows(OwnerType.MEMBER, OTHER_ID)).isTrue();
            assertThatThrownBy(() -> cartService.removeItem(member(BUYER_ID), CART_ITEM_ID))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_RESERVED);
            assertThatThrownBy(() -> cartService.changeQuantity(member(BUYER_ID), CART_ITEM_ID, 2))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_RESERVED);
            verify(cartItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("다른 예약에 든 칸은 409, 없는 칸 404, 빈 목록 · 예약 ID · 모르는 주체 종류는 400")
        void reserveRejects() {
            CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
            item.reserve("r-0");
            when(cartItemRepository.findAllByIdIn(any())).thenReturn(List.of(item)).thenReturn(List.of());
            List<CartReservations.Payer> payers = List.of(new CartReservations.Payer("MEMBER", OTHER_ID));

            assertThatThrownBy(() -> cartService.reserve(List.of(CART_ITEM_ID), payers, "r-1"))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_RESERVED);
            assertThatThrownBy(() -> cartService.reserve(List.of(CART_ITEM_ID), payers, "r-1"))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cartService.reserve(List.of(), payers, "r-1"))
                .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_RESERVATION);
            assertThatThrownBy(() -> cartService.reserve(List.of(CART_ITEM_ID), payers, " "))
                .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_RESERVATION);
            assertThatThrownBy(() -> cartService.reserve(List.of(CART_ITEM_ID),
                List.of(new CartReservations.Payer("ADMIN", 1L)), "r-1"))
                .extracting("errorCode").isEqualTo(CartErrorCode.INVALID_RESERVATION);
        }

        @Test
        @DisplayName("예약을 풀면 칸이 다시 바뀔 수 있고 허용 주체가 지워진다")
        void release() {
            CartItem item = line(BUYER_ID, OPTION_ID, null, 1);
            item.reserve("r-1");
            CartReservation payer = CartReservation.allow("r-1", OwnerType.MEMBER, OTHER_ID);
            when(cartItemRepository.findAllByReservationId("r-1")).thenReturn(List.of(item));
            when(reservationRepository.findAllByReservationId("r-1")).thenReturn(List.of(payer));

            cartService.release("r-1");

            assertThat(item.isReserved()).isFalse();
            verify(reservationRepository).delete(payer);
        }

        @Test
        @DisplayName("주문 가능: 내 칸 · 내가 허용된 예약 칸은 통과. 같은 가족인데 허용 안 됨 · 옵션/자녀 다름은 403, "
            + "다른 가족의 칸(허용 안 됨) · 없는 칸은 404(존재를 숨김)")
        void requireOrderable() {
            CartItem mine = line(BUYER_ID, OPTION_ID, null, 1);
            CartItem reservedForMe = CartItem.create(OwnerType.CHILD, CHILD_ID, OTHER_ID, 3L, CHILD_ID, 1);
            reservedForMe.reserve("r-1");
            CartItem others = line(OTHER_ID, 4L, null, 1);
            CartItem familyNotAllowed = CartItem.create(OwnerType.CHILD, CHILD_ID, BUYER_ID, 6L, CHILD_ID, 1);
            when(cartItemRepository.findById(4L)).thenReturn(Optional.of(familyNotAllowed));
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(mine));
            when(cartItemRepository.findById(2L)).thenReturn(Optional.of(reservedForMe));
            when(cartItemRepository.findById(3L)).thenReturn(Optional.of(others));
            when(reservationRepository.findAllByReservationId("r-1"))
                .thenReturn(List.of(CartReservation.allow("r-1", OwnerType.MEMBER, BUYER_ID)));
            CartReservations.Payer buyer = new CartReservations.Payer("MEMBER", BUYER_ID);

            cartService.requireOrderable(buyer, List.of(new CartCheckout.Line(1L, OPTION_ID, null, 1),
                new CartCheckout.Line(2L, 3L, CHILD_ID, 1)));

            assertThatThrownBy(() -> cartService.requireOrderable(buyer,
                List.of(new CartCheckout.Line(3L, 4L, null, 1))))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cartService.requireOrderable(new CartReservations.Payer("MEMBER", 99L),
                List.of(new CartCheckout.Line(2L, 3L, CHILD_ID, 1))))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cartService.requireOrderable(buyer,
                List.of(new CartCheckout.Line(4L, 6L, CHILD_ID, 1))))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_ORDERABLE);
            assertThatThrownBy(() -> cartService.requireOrderable(buyer,
                List.of(new CartCheckout.Line(1L, 9L, null, 1))))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_ORDERABLE);
            assertThatThrownBy(() -> cartService.requireOrderable(buyer,
                List.of(new CartCheckout.Line(5L, 1L, null, 1))))
                .extracting("errorCode").isEqualTo(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("예약 칸 주문 수량(#998)")
    class ReservedQuantity {

        @Test
        @DisplayName("허용된 예약 칸은 주문 수량이 칸 수량과 같으면 통과, 넘으면 409(RESERVED_QUANTITY_EXCEEDED). "
            + "자기 칸은 칸 수량보다 많아도 통과")
        void reservedLineQuantityLimited() {
            CartItem reservedForMe = CartItem.create(OwnerType.CHILD, CHILD_ID, OTHER_ID, 3L, CHILD_ID, 2);
            reservedForMe.reserve("r-1");
            CartItem mine = line(BUYER_ID, OPTION_ID, null, 1);
            when(cartItemRepository.findById(1L)).thenReturn(Optional.of(mine));
            when(cartItemRepository.findById(2L)).thenReturn(Optional.of(reservedForMe));
            when(reservationRepository.findAllByReservationId("r-1"))
                .thenReturn(List.of(CartReservation.allow("r-1", OwnerType.MEMBER, BUYER_ID)));
            CartReservations.Payer buyer = new CartReservations.Payer("MEMBER", BUYER_ID);

            cartService.requireOrderable(buyer, List.of(new CartCheckout.Line(2L, 3L, CHILD_ID, 2),
                new CartCheckout.Line(1L, OPTION_ID, null, 5)));

            assertThatThrownBy(() -> cartService.requireOrderable(buyer,
                List.of(new CartCheckout.Line(2L, 3L, CHILD_ID, 3))))
                .extracting("errorCode").isEqualTo(CartErrorCode.RESERVED_QUANTITY_EXCEEDED);
        }
    }

    private static CartActors.Actor member(Long memberId) {
        return new CartActors.Actor(OwnerType.MEMBER, memberId, memberId);
    }

    private static CartActors.Actor childActor() {
        return new CartActors.Actor(OwnerType.CHILD, CHILD_ID, BUYER_ID);
    }

    private static CartItem line(Long memberId, Long optionId, Long childId, int quantity) {
        return CartItem.create(OwnerType.MEMBER, memberId, memberId, optionId, childId, quantity);
    }

    private void assertAddFails(Long memberId, Long optionId, Long childId, int quantity,
        CartErrorCode expected) {
        assertThatThrownBy(() -> cartService.addItem(member(memberId), optionId, childId, quantity))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(expected);
        verify(cartItemRepository, never()).save(any());
        verifyNoEvent();
    }

    private void verifyNoEvent() {
        verify(eventPublisher, never()).publish(any());
        verify(outbox, never()).append(any());
    }

    /**
     * 같은 이벤트가 Spring 이벤트와 아웃박스 양쪽에 한 번씩 나갔는지 보고 그 이벤트를 돌려준다.
     */
    private CartItemAddedEvent publishedEvent() {
        return (CartItemAddedEvent) publishedDomainEvent();
    }

    private DomainEvent publishedDomainEvent() {
        ArgumentCaptor<DomainEvent> published = ArgumentCaptor.forClass(DomainEvent.class);
        ArgumentCaptor<DomainEvent> appended = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(published.capture());
        verify(outbox).append(appended.capture());
        assertThat(appended.getValue()).isSameAs(published.getValue());
        return published.getValue();
    }

    private void givenProduct(ReplicaProduct product) {
        when(productReplicaRepository.findByOptionId(product.getOptionId())).thenReturn(Optional.of(product));
    }

    private void givenChild(ReplicaChild child) {
        when(childReplicaRepository.findByChildId(CHILD_ID)).thenReturn(Optional.of(child));
    }

    private static ReplicaProduct product(Long optionId, long unitPrice, String status, boolean soldOut) {
        return ReplicaProduct.create(new ReplicaProduct.Values(optionId, 1L, SELLER_ID, "상품 A", null, "110", unitPrice,
            1L, status, null, soldOut, OCCURRED_AT));
    }

    private static ReplicaChild child(Long memberId, boolean deleted) {
        return ReplicaChild.create(new ReplicaChild.Values(CHILD_ID, memberId, null, new BigDecimal("115.0"),
            new BigDecimal("20.0"), "NORMAL", deleted, OCCURRED_AT));
    }
}
