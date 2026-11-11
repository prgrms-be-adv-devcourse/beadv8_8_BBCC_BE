package com.bbcc.kidly.boundedcontext.cart.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 칸 예약의 결제 정리 표시(#999)는 장바구니의 일반 기능이다: 예약한 쪽이 누구든(조르기가 아닌 예약 ID여도) 결제 정리가 예약 칸을
 * 정리하면 그 예약의 허용 주체 행에 시각이 남고 hasPurchase가 true다.
 */
@IntegrationTest
class CartReservationPurchaseIntegrationTest {

    private static final Long OPTION_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 3L;
    private static final String RESERVATION_ID = "other-1";

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
    }

    @Test
    @DisplayName("조르기와 무관한 예약 ID로 예약한 칸을 일부 수량만 결제 정리해도 칸 · 예약은 남고, 허용 주체 행 모두에 결제 정리 시각이 "
        + "남아 hasPurchase가 true다")
    void marksPurchaseForAnyReservation() {
        Long line = cartService.addItem(CartOwners.member(BUYER_ID), OPTION_ID, null, 3).line().cartItemId();
        cartService.reserve(List.of(line), List.of(new CartReservations.Payer("MEMBER", BUYER_ID),
            new CartReservations.Payer("MEMBER", OTHER_MEMBER_ID)), RESERVATION_ID, null);
        assertThat(cartService.hasPurchase(RESERVATION_ID)).isFalse();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        cartService.clearOrdered(900L,
            List.of(new CartCleanup.OrderedLine(null, line, BUYER_ID, OPTION_ID, null, 1)));

        assertThat(cartService.hasPurchase(RESERVATION_ID)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "select quantity from orders.cart_item where id = ? and reservation_id = ?", Integer.class, line,
            RESERVATION_ID)).isEqualTo(2);
        List<LocalDateTime> marked = jdbcTemplate.queryForList(
            "select purchased_at from orders.cart_reservation where reservation_id = ? order by id",
            LocalDateTime.class, RESERVATION_ID);
        assertThat(marked).hasSize(2).doesNotContainNull().allSatisfy(at -> assertThat(at).isAfter(before));
        assertThat(marked.get(1)).isEqualTo(marked.getFirst());
        assertThat(jdbcTemplate.queryForList("select version from orders.cart_reservation where reservation_id = ?",
            Long.class, RESERVATION_ID)).containsOnly(1L);
    }
}
