package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderRequestFingerprintTest {

    private static final PlaceOrderCommand.ShippingAddress ADDRESS =
        new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1");
    private static final PlaceOrderCommand.Item SOCKS = new PlaceOrderCommand.Item(1L, null, 2, 25_000L);
    private static final PlaceOrderCommand.Item SOCKS_FOR_CHILD = new PlaceOrderCommand.Item(1L, 7L, 1, 25_000L);
    private static final PlaceOrderCommand.Item SHIRT = new PlaceOrderCommand.Item(2L, 7L, 1, 30_000L);

    @Test
    @DisplayName("비교값은 SHA-256 소문자 16진수 64자이고 같은 요청이면 같다")
    void sameRequestSameValue() {
        String first = OrderRequestFingerprint.of(command(List.of(SOCKS, SHIRT), ADDRESS));
        String second = OrderRequestFingerprint.of(command(List.of(SOCKS, SHIRT), ADDRESS));

        assertThat(first).matches("[0-9a-f]{64}").isEqualTo(second);
    }

    @Test
    @DisplayName("items 순서만 다르면 같은 값이다(optionId → childId(null 먼저) 순으로 정렬)")
    void orderOfItemsDoesNotMatter() {
        String ordered = OrderRequestFingerprint.of(command(List.of(SOCKS, SOCKS_FOR_CHILD, SHIRT), ADDRESS));
        String shuffled = OrderRequestFingerprint.of(command(List.of(SHIRT, SOCKS_FOR_CHILD, SOCKS), ADDRESS));

        assertThat(shuffled).isEqualTo(ordered);
    }

    @Test
    @DisplayName("수량 · 단가 · 옵션 · 자녀 · 배송지 칸 중 하나만 달라도 다른 값이다")
    void anyFieldChangesValue() {
        String base = OrderRequestFingerprint.of(command(List.of(SOCKS), ADDRESS));

        assertThat(List.of(
            OrderRequestFingerprint.of(command(List.of(new PlaceOrderCommand.Item(1L, null, 3, 25_000L)), ADDRESS)),
            OrderRequestFingerprint.of(command(List.of(new PlaceOrderCommand.Item(1L, null, 2, 25_001L)), ADDRESS)),
            OrderRequestFingerprint.of(command(List.of(new PlaceOrderCommand.Item(9L, null, 2, 25_000L)), ADDRESS)),
            OrderRequestFingerprint.of(command(List.of(new PlaceOrderCommand.Item(1L, 7L, 2, 25_000L)), ADDRESS)),
            OrderRequestFingerprint.of(command(List.of(SOCKS),
                new PlaceOrderCommand.ShippingAddress("홍길순", "010-0000-0000", "12345", "서울시 테스트로 1"))),
            OrderRequestFingerprint.of(command(List.of(SOCKS),
                new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0001", "12345", "서울시 테스트로 1"))),
            OrderRequestFingerprint.of(command(List.of(SOCKS),
                new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12346", "서울시 테스트로 1"))),
            OrderRequestFingerprint.of(command(List.of(SOCKS),
                new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 2")))))
            .doesNotContain(base)
            .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("값 안에 구분자가 들어 있어도 칸 경계가 다른 두 요청은 다른 값이다")
    void separatorInsideValueDoesNotCollide() {
        String split = OrderRequestFingerprint.of(command(List.of(SOCKS),
            new PlaceOrderCommand.ShippingAddress("a:1", "b", "12345", "주소")));
        String joined = OrderRequestFingerprint.of(command(List.of(SOCKS),
            new PlaceOrderCommand.ShippingAddress("a", "1:b", "12345", "주소")));

        assertThat(split).isNotEqualTo(joined);
    }

    private static PlaceOrderCommand command(List<PlaceOrderCommand.Item> items,
        PlaceOrderCommand.ShippingAddress address) {
        return new PlaceOrderCommand(items, address);
    }
}
