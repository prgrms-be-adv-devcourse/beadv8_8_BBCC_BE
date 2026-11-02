package com.bbcc.kidly.boundedcontext.wish.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.Approval;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.Checkout;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.OrderResult;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException.Rule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 조르기 규칙(#701): 보내기 · 진행 중 줄 잠금 · 일부 승인 · 수량 변경 · 거절 · 한 번만 처리 · 바로 결제 결과 반영.
 */
class WishRequestTest {

    private static final Long CHILD_ID = 7L;
    private static final Long PARENT_ID = 1L;
    private static final Long REQUEST_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 14, 0);

    private WishItem first;
    private WishItem second;
    private long idSeq;

    @BeforeEach
    void setUp() {
        first = item(10L, 1L, 2);
        second = item(11L, 2L, 1);
    }

    @Nested
    @DisplayName("보내기")
    class Send {

        @Test
        @DisplayName("줄 여러 개 + 메시지로 만들고, 저장 뒤 든 줄을 모두 잠근다(줄은 공용 장바구니에 남는다)")
        void sendsAndLocks() {
            WishRequest request = sent(first, second);

            assertThat(request.getStatus()).isEqualTo(WishRequest.Status.REQUESTED);
            assertThat(request.getMessage()).isEqualTo("사 주세요");
            assertThat(request.getLines()).extracting(WishRequestLine::getWishItemId, WishRequestLine::getOptionId,
                    WishRequestLine::getRequestedQuantity, WishRequestLine::getApprovedQuantity)
                .containsExactly(tuple(10L, 1L, 2, null),
                    tuple(11L, 2L, 1, null));
            assertThat(first.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(second.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(first.getStatus()).isEqualTo(WishItem.Status.ACTIVE);
        }

        @Test
        @DisplayName("메시지는 선택이다: 비었거나 공백뿐이면 null, 앞뒤 공백은 지운다")
        void optionalMessage() {
            assertThat(WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, "  ", 100, List.of(first)).getMessage())
                .isNull();
            assertThat(WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, " 응 ", 100, List.of(second)).getMessage())
                .isEqualTo("응");
        }

        @Test
        @DisplayName("진행 중인 조르기에 든 줄은 다른 조르기에 넣을 수 없다")
        void lockedItemRejected() {
            sent(first);

            assertRule(() -> WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, null, 100, List.of(second, first)),
                Rule.WISH_ITEM_IN_REQUEST);
            assertThat(second.isLocked()).isFalse();
        }

        @Test
        @DisplayName("줄이 없거나 · 겹치거나 · 다른 자녀 줄이거나 · 메시지가 정책보다 길면 거절한다")
        void invalidRequest() {
            assertRule(() -> WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, null, 100, List.of()),
                Rule.INVALID_WISH_REQUEST);
            assertRule(() -> WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, null, 100, List.of(first, first)),
                Rule.INVALID_WISH_REQUEST);
            assertRule(() -> WishRequest.send(8L, AddedBy.PARENT, PARENT_ID, null, 100, List.of(first)),
                Rule.INVALID_WISH_REQUEST);
            assertRule(() -> WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, "가".repeat(11), 10, List.of(first)),
                Rule.INVALID_WISH_REQUEST);
            assertThat(WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, "가".repeat(10), 10, List.of(first))
                .getMessage()).hasSize(10);
        }

        @Test
        @DisplayName("잠긴 줄은 수량을 바꿀 수 없다")
        void lockedItemQuantityFixed() {
            sent(first);

            assertRule(() -> first.addQuantity(1), Rule.WISH_ITEM_IN_REQUEST);
            assertThat(first.getQuantity()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("일부만 · 수량을 바꿔 승인할 수 있다. 바로 결제면 승인한 줄은 결제 결과까지 잠기고 나머지는 풀린다")
        void partialOrder() {
            WishRequest request = sent(first, second);

            List<WishRequestLine> approved = request.approve(List.of(new Approval(10L, 5)), Checkout.ORDER, PARENT_ID,
                List.of(first, second), NOW);

            assertThat(approved).extracting(WishRequestLine::getWishItemId, WishRequestLine::getApprovedQuantity)
                .containsExactly(tuple(10L, 5));
            assertThat(request.getStatus()).isEqualTo(WishRequest.Status.APPROVED);
            assertThat(request.getCheckout()).isEqualTo(Checkout.ORDER);
            assertThat(request.getDecidedById()).isEqualTo(PARENT_ID);
            assertThat(request.getDecidedAt()).isEqualTo(NOW);
            assertThat(first.isLocked()).isTrue();
            assertThat(second.isLocked()).isFalse();
            assertThat(first.getQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("보호자 장바구니로 옮기기면 승인과 함께 모든 줄이 풀린다(줄은 공용 장바구니에 남는다)")
        void cartUnlocksAll() {
            WishRequest request = sent(first, second);

            request.approve(List.of(new Approval(10L, 1), new Approval(11L, 1)), Checkout.CART, PARENT_ID,
                List.of(first, second), NOW);

            assertThat(first.isLocked()).isFalse();
            assertThat(second.isLocked()).isFalse();
            assertThat(first.getStatus()).isEqualTo(WishItem.Status.ACTIVE);
            assertThat(request.isAwaitingOrder()).isFalse();
        }

        @Test
        @DisplayName("이미 승인 · 거절한 조르기는 다시 처리할 수 없다")
        void onlyOnce() {
            WishRequest approved = sent(first);
            approved.approve(List.of(new Approval(10L, 1)), Checkout.CART, PARENT_ID, List.of(first), NOW);
            WishRequest rejected = sent(second);
            rejected.reject(RejectReason.NEXT_TIME, PARENT_ID, List.of(second), NOW);

            assertRule(() -> approved.approve(List.of(new Approval(10L, 1)), Checkout.ORDER, PARENT_ID,
                List.of(first), NOW), Rule.WISH_REQUEST_ALREADY_DECIDED);
            assertRule(() -> approved.reject(RejectReason.OTHER, PARENT_ID, List.of(first), NOW),
                Rule.WISH_REQUEST_ALREADY_DECIDED);
            assertRule(() -> rejected.approve(List.of(new Approval(11L, 1)), Checkout.CART, PARENT_ID,
                List.of(second), NOW), Rule.WISH_REQUEST_ALREADY_DECIDED);
        }

        @Test
        @DisplayName("고른 줄이 없거나 · 조르기에 없는 줄이거나 · 겹치면 거절하고 아무것도 바꾸지 않는다")
        void invalidApproval() {
            WishRequest request = sent(first, second);

            assertRule(() -> request.approve(List.of(), Checkout.ORDER, PARENT_ID, List.of(first, second), NOW),
                Rule.INVALID_APPROVAL);
            assertRule(() -> request.approve(List.of(new Approval(99L, 1)), Checkout.ORDER, PARENT_ID,
                List.of(first, second), NOW), Rule.INVALID_APPROVAL);
            assertRule(() -> request.approve(List.of(new Approval(10L, 1), new Approval(10L, 2)), Checkout.ORDER,
                PARENT_ID, List.of(first, second), NOW), Rule.INVALID_APPROVAL);
            assertThat(request.getStatus()).isEqualTo(WishRequest.Status.REQUESTED);
            assertThat(request.getLines()).allMatch(line -> !line.isApproved());
            assertThat(first.isLocked()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 100})
        @DisplayName("승인 수량은 1~99다")
        void approvedQuantityRange(int quantity) {
            WishRequest request = sent(first);

            assertRule(() -> request.approve(List.of(new Approval(10L, quantity)), Checkout.CART, PARENT_ID,
                List.of(first), NOW), Rule.INVALID_APPROVAL);
        }
    }

    @Test
    @DisplayName("거절하면 사유를 남기고 든 줄의 잠금을 모두 푼다")
    void reject() {
        WishRequest request = sent(first, second);

        request.reject(RejectReason.TOO_EXPENSIVE, PARENT_ID, List.of(first, second), NOW);

        assertThat(request.getStatus()).isEqualTo(WishRequest.Status.REJECTED);
        assertThat(request.getRejectReason()).isEqualTo(RejectReason.TOO_EXPENSIVE);
        assertThat(first.isLocked()).isFalse();
        assertThat(second.isLocked()).isFalse();
    }

    @Nested
    @DisplayName("바로 결제 결과")
    class SettleOrder {

        private WishRequest request;

        @BeforeEach
        void ordered() {
            request = sent(first, second);
            request.approve(List.of(new Approval(10L, 1)), Checkout.ORDER, PARENT_ID, List.of(first, second), NOW);
            request.recordOrder(500L);
        }

        @Test
        @DisplayName("결제되면 승인한 줄만 공용 장바구니에서 빠지고(PURCHASED) 결과는 한 번만 정해진다")
        void paid() {
            assertThat(request.settleOrder(true, false, List.of(first, second))).contains(OrderResult.PAID);

            assertThat(first.getStatus()).isEqualTo(WishItem.Status.PURCHASED);
            assertThat(first.isLocked()).isFalse();
            assertThat(second.getStatus()).isEqualTo(WishItem.Status.ACTIVE);
            assertThat(request.isAwaitingOrder()).isFalse();
            assertThat(request.settleOrder(true, true, List.of(first, second))).isEmpty();
            assertThat(request.getOrderResult()).isEqualTo(OrderResult.PAID);
        }

        @Test
        @DisplayName("결제 뒤 취소된 주문도 결제된 것으로 본다(본 시점과 상관없이 결과가 같다)")
        void paidThenCancelled() {
            assertThat(request.settleOrder(true, true, List.of(first, second))).contains(OrderResult.PAID);
            assertThat(first.getStatus()).isEqualTo(WishItem.Status.PURCHASED);
        }

        @Test
        @DisplayName("결제 없이 취소되면(결제 실패 · 마감 등) 승인한 줄의 잠금만 풀리고 공용 장바구니에 남는다")
        void cancelled() {
            assertThat(request.settleOrder(false, true, List.of(first, second))).contains(OrderResult.CANCELLED);

            assertThat(first.getStatus()).isEqualTo(WishItem.Status.ACTIVE);
            assertThat(first.isLocked()).isFalse();
        }

        @Test
        @DisplayName("결제대기면 아무것도 바꾸지 않는다(승인한 줄은 잠긴 채)")
        void pending() {
            assertThat(request.settleOrder(false, false, List.of(first, second))).isEmpty();

            assertThat(first.isLocked()).isTrue();
            assertThat(request.isAwaitingOrder()).isTrue();
        }
    }

    private WishRequest sent(WishItem... items) {
        WishRequest request = WishRequest.send(CHILD_ID, AddedBy.CHILD, CHILD_ID, "사 주세요", 100, List.of(items));
        ReflectionTestUtils.setField(request, "id", REQUEST_ID + idSeq++);
        request.lockItems(List.of(items));
        return request;
    }

    private static WishItem item(Long id, Long optionId, int quantity) {
        WishItem item = WishItem.create(CHILD_ID, optionId, quantity, AddedBy.CHILD, CHILD_ID);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static void assertRule(Executable call, Rule rule) {
        assertThatThrownBy(call::execute)
            .isInstanceOfSatisfying(WishRuleViolationException.class, e -> assertThat(e.getRule()).isEqualTo(rule));
    }
}
