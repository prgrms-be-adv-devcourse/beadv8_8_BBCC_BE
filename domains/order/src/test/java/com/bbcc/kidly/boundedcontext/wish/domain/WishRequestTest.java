package com.bbcc.kidly.boundedcontext.wish.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.CancelReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.LineValue;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.Status;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException.Rule;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WishRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final int MAX_MESSAGE = 100;

    @Test
    @DisplayName("보내면 종류 WISH · 대기 상태이고, 메시지는 앞뒤 공백을 지운다(공백뿐이면 없음)")
    void send() {
        WishRequest request = WishRequest.send(PrincipalType.CHILD, 1L, 1L, "  사 주세요  ", MAX_MESSAGE);
        WishRequest blank = WishRequest.send(PrincipalType.CHILD, 1L, 1L, "   ", MAX_MESSAGE);

        assertThat(request.getType()).isEqualTo(WishRequest.Type.WISH);
        assertThat(request.getStatus()).isEqualTo(Status.REQUESTED);
        assertThat(request.getMessage()).isEqualTo("사 주세요");
        assertThat(request.isRequestedBy(PrincipalType.CHILD, 1L)).isTrue();
        assertThat(request.isRequestedBy(PrincipalType.MEMBER, 1L)).isFalse();
        assertThat(blank.getMessage()).isNull();
    }

    @Test
    @DisplayName("메시지가 정책 길이를 넘으면 INVALID_WISH_REQUEST")
    void messageTooLong() {
        assertThatThrownBy(() -> WishRequest.send(PrincipalType.CHILD, 1L, 1L, "가".repeat(MAX_MESSAGE + 1),
            MAX_MESSAGE))
            .isInstanceOfSatisfying(WishRuleViolationException.class,
                e -> assertThat(e.getRule()).isEqualTo(Rule.INVALID_WISH_REQUEST));
    }

    @Test
    @DisplayName("칸은 한 번만, 하나 이상 겹치지 않게 담는다")
    void addLines() {
        WishRequest request = requested();

        assertThat(request.getLines()).hasSize(2);
        assertThat(request.cartItemIds()).containsExactly(10L, 11L);
        assertThat(request.containsCartItem(11L)).isTrue();
        assertThat(request.containsCartItem(12L)).isFalse();
        assertThat(request.containsCartItem(null)).isFalse();
        assertViolation(() -> request.addLines(List.of(new LineValue(12L, null, 1L, 1))), Rule.INVALID_WISH_REQUEST);

        WishRequest empty = WishRequest.send(PrincipalType.CHILD, 1L, 1L, null, MAX_MESSAGE);
        assertViolation(() -> empty.addLines(List.of()), Rule.INVALID_WISH_REQUEST);
        assertViolation(() -> empty.addLines(List.of(new LineValue(10L, null, 1L, 1), new LineValue(10L, null, 1L, 1))),
            Rule.INVALID_WISH_REQUEST);
        assertViolation(() -> empty.addLines(List.of(new LineValue(null, null, 1L, 1))), Rule.INVALID_WISH_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wish-", "wish-abc", "wish--1", "order-1", "local-998", "wish-1234567890123456789", ""})
    @DisplayName("조르기 예약이 아닌 예약 ID에서는 조르기 ID를 꺼내지 않는다")
    void notWishReservation(String reservationId) {
        assertThat(WishRequest.idOfReservation(reservationId)).isEmpty();
    }

    @Test
    @DisplayName("조르기 예약 ID는 wish-{조르기 ID}다")
    void wishReservation() {
        assertThat(WishRequest.idOfReservation("wish-42")).contains(42L);
        assertThat(WishRequest.idOfReservation(null)).isEmpty();
    }

    @Test
    @DisplayName("결제되면 한 번만 결제 완료가 되고 결제한 사람 · 시각을 남긴다. 다시 결제돼도 바뀌지 않는다")
    void pay() {
        WishRequest request = requested();

        assertThat(request.pay(PrincipalType.MEMBER, 7L, NOW)).isTrue();
        assertThat(request.pay(PrincipalType.MEMBER, 8L, NOW.plusMinutes(1))).isFalse();

        assertThat(request.getStatus()).isEqualTo(Status.PAID);
        assertThat(request.getPayerType()).isEqualTo(PrincipalType.MEMBER);
        assertThat(request.getPayerId()).isEqualTo(7L);
        assertThat(request.getPaidAt()).isEqualTo(NOW);
        assertViolation(() -> request.reject(RejectReason.OTHER, PrincipalType.MEMBER, 7L, NOW),
            Rule.WISH_REQUEST_ALREADY_DECIDED);
        assertViolation(() -> request.cancel(CancelReason.DIRECT, NOW), Rule.WISH_REQUEST_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("거절하면 사유 · 거절한 사람 · 시각을 남기고, 거절 · 철회된 조르기는 결제 완료 · 다시 처리가 409다")
    void rejectAndCancel() {
        WishRequest rejected = requested();
        rejected.reject(RejectReason.NEXT_TIME, PrincipalType.MEMBER, 7L, NOW);
        WishRequest cancelled = requested();
        cancelled.cancel(CancelReason.DIRECT, NOW);

        assertThat(rejected.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.NEXT_TIME);
        assertThat(rejected.getRejectedByType()).isEqualTo(PrincipalType.MEMBER);
        assertThat(rejected.getRejectedById()).isEqualTo(7L);
        assertThat(rejected.getRejectedAt()).isEqualTo(NOW);
        assertThat(cancelled.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo(CancelReason.DIRECT);
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
        for (WishRequest done : List.of(rejected, cancelled)) {
            assertViolation(() -> done.pay(PrincipalType.MEMBER, 7L, NOW), Rule.WISH_REQUEST_ALREADY_DECIDED);
            assertViolation(() -> done.cancel(CancelReason.SYSTEM, NOW), Rule.WISH_REQUEST_ALREADY_DECIDED);
            assertViolation(() -> done.reject(RejectReason.OTHER, PrincipalType.MEMBER, 7L, NOW),
                Rule.WISH_REQUEST_ALREADY_DECIDED);
        }
    }

    private static WishRequest requested() {
        WishRequest request = WishRequest.send(PrincipalType.CHILD, 1L, 1L, null, MAX_MESSAGE);
        request.addLines(List.of(new LineValue(10L, 100L, 1L, 2), new LineValue(11L, null, 2L, 1)));
        return request;
    }

    private static void assertViolation(Runnable action, Rule rule) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WishRuleViolationException.class,
            e -> assertThat(e.getRule()).isEqualTo(rule));
    }
}
