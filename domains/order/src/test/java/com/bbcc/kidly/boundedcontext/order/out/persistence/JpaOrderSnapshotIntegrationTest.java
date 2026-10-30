package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 주문상품 스냅샷 칸의 DB 제약: 옵션 이름 길이(255) · 사이즈 null 허용.
 */
@IntegrationTest
class JpaOrderSnapshotIntegrationTest {

    private static final Long BUYER_ID = 1L;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("옵션 이름 255자 · 사이즈 null인 주문상품 스냅샷을 저장하고 그대로 다시 읽는다")
    void storesLongOptionNameAndNullSize() {
        String optionName = "가".repeat(255);
        Order order = orderRepository.save(place(optionName, null));

        OrderItem.ProductSnapshot stored = orderRepository.findAllWithItemsByIdIn(List.of(order.getId()))
            .getFirst().getItems().getFirst().getProduct();
        assertThat(stored.optionName()).hasSize(255);
        assertThat(stored.size()).isNull();
    }

    @Test
    @DisplayName("옵션 이름이 256자면 주문상품 저장이 DB 제약으로 실패한다")
    void rejectsOptionNameOver255() {
        assertThatThrownBy(() -> orderRepository.save(place("가".repeat(256), "110")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Order place(String optionName, String size) {
        return Order.place(BUYER_ID, List.of(new Order.RequestedItem(
                new OrderItem.ProductSnapshot(1L, 1L, 2L, "상품", optionName, size, 10_000L, 1L),
                true, null, 1, 10_000L)),
            new Order.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1"),
            new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)), LocalDateTime.now());
    }
}
