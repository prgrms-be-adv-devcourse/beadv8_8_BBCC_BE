package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderResult;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderPlacementTest {

    private static final Long MEMBER_ID = 1L;
    private static final String KEY = "3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d";
    private static final PlaceOrderCommand COMMAND = new PlaceOrderCommand(
        List.of(new PlaceOrderCommand.Item(1L, null, 2, 25_000L)),
        new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"));
    private static final String HASH = OrderRequestFingerprint.of(COMMAND);

    @Mock
    private OrderService orderService;

    @Mock
    private OrderDetail detail;

    private OrderPlacement orderPlacement;

    @BeforeEach
    void setUp() {
        orderPlacement = new OrderPlacement(orderService);
    }

    @Test
    @DisplayName("키가 있으면 요청 비교값을 만들어 주문하기에 넘기고 그 결과를 돌려준다")
    void passesKeyAndHash() {
        PlaceOrderResult created = new PlaceOrderResult(detail, true);
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, KEY, HASH)).thenReturn(created);

        assertThat(orderPlacement.place(MEMBER_ID, COMMAND, KEY)).isSameAs(created);
    }

    @Test
    @DisplayName("키가 없으면 키 · 비교값 없이 주문하기에 넘긴다")
    void passesNullsWithoutKey() {
        PlaceOrderResult created = new PlaceOrderResult(detail, true);
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, null, null)).thenReturn(created);

        assertThat(orderPlacement.place(MEMBER_ID, COMMAND, null)).isSameAs(created);
    }

    @Test
    @DisplayName("대문자로 쓴 키는 소문자로 바꿔 넘긴다")
    void normalizesKeyToLowerCase() {
        PlaceOrderResult created = new PlaceOrderResult(detail, true);
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, KEY, HASH)).thenReturn(created);

        assertThat(orderPlacement.place(MEMBER_ID, COMMAND, KEY.toUpperCase())).isSameAs(created);
    }

    @ParameterizedTest(name = "키 {0}")
    @ValueSource(strings = {"abc", "1-1-1-1-1", "3f1c2b9a0d4e4c7b9a511e2f3a4b5c6d",
        "3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6g",
        "3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d0", "", "{3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4b5c6d}",
        "3f1c2b9a-0d4e-4c7b-9a51-1e2f3a4 5c6d", "3f1c2b9a0-d4e-4c7b-9a51-1e2f3a4b5c6d"})
    @DisplayName("UUID 표준 표기(8-4-4-4-12, 36자)가 아닌 키는 INVALID_IDEMPOTENCY_KEY, 주문하기를 부르지 않는다")
    void rejectsInvalidKey(String key) {
        assertThatThrownBy(() -> orderPlacement.place(MEMBER_ID, COMMAND, key))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(OrderErrorCode.INVALID_IDEMPOTENCY_KEY);
        verify(orderService, never()).placeIdempotently(any(), any(), any(), any());
    }

    @Test
    @DisplayName("동시에 같은 키로 저장해 요청 키 유니크 제약에 걸리면 새 트랜잭션에서 다시 읽은 결과를 돌려준다")
    void replaysOnKeyConstraintViolation() {
        PlaceOrderResult replayed = new PlaceOrderResult(detail, false);
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, KEY, HASH)).thenThrow(keyViolation());
        when(orderService.replay(MEMBER_ID, KEY, HASH)).thenReturn(Optional.of(replayed));

        assertThat(orderPlacement.place(MEMBER_ID, COMMAND, KEY)).isSameAs(replayed);
    }

    @Test
    @DisplayName("요청 키가 아닌 다른 무결성 오류면 다시 읽지 않고 그대로 던진다")
    void rethrowsOtherViolation() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("could not execute statement",
            new RuntimeException("ERROR: null value in column \"member_id\" violates not-null constraint"));
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, KEY, HASH)).thenThrow(other);

        assertThatThrownBy(() -> orderPlacement.place(MEMBER_ID, COMMAND, KEY)).isSameAs(other);
        verify(orderService, never()).replay(any(), any(), any());
    }

    @Test
    @DisplayName("요청 키 제약에 걸렸는데 다시 읽은 주문이 없으면 원래 오류를 던진다")
    void rethrowsWhenReplayFindsNothing() {
        DataIntegrityViolationException violation = keyViolation();
        when(orderService.placeIdempotently(MEMBER_ID, COMMAND, KEY, HASH)).thenThrow(violation);
        when(orderService.replay(MEMBER_ID, KEY, HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderPlacement.place(MEMBER_ID, COMMAND, KEY)).isSameAs(violation);
    }

    private static DataIntegrityViolationException keyViolation() {
        return new DataIntegrityViolationException("could not execute statement",
            new RuntimeException("ERROR: duplicate key value violates unique constraint "
                + "\"uk_orders_member_idempotency_key\""));
    }
}
