package com.bbcc.kidly.boundedcontext.wish.in.scheduler;

import static com.bbcc.kidly.boundedcontext.cart.in.web.CartController.CART_ITEMS_PATH;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.in.web.OrderController.ORDERS_PATH;
import static com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController.WISH_REQUESTS_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildAccountEventListener;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.boundedcontext.wish.app.WishExpiry;
import com.bbcc.kidly.boundedcontext.wish.app.WishReservationValidUntilService;
import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 조르기 예약의 결제 가능 기한(#1008, 사용자 결정 3 ①): 보낼 때 장바구니 예약에 기한(보낸 시각 + ttl)을 두고, 보호자가 기한 지난 예약
 * 칸으로 주문하면 만료 작업 전이라도 409(RESERVATION_EXPIRED)다. 설정(ttl · 켜짐)이 바뀌면 진행 중 조르기의 예약 기한을 다시 맞춘다.
 * 테스트 설정은 ttl 기본값(P7D) · 켜짐이고, 다른 설정은 같은 저장소 · 장바구니 빈에 설정만 바꾼 계산기로 다시 맞춰 본다(기동 때 맞추기와
 * 같은 서비스). 자녀 1의 보호자는 BUYER_ID, 자녀 계정 100 → 1.
 */
@FunctionalTest
class WishReservationValidUntilApiTest {

    private static final Long CHILD_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;
    private static final long PRICE = 25_000L;
    private static final Duration INTERVAL = Duration.ofHours(1);
    private static final Duration GRACE = Duration.ofMinutes(15);
    private static final Map<String, String> ADDRESS = Map.of(
        "recipientName", "홍길동", "phone", "010-0000-0000", "zipCode", "12345", "address", "서울시 테스트로 1");

    @Autowired
    private RestTestClient client;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @Autowired
    private ChildAccountEventListener childAccountEventListener;

    @Autowired
    private WishRequestRepository wishRequestRepository;

    @Autowired
    private WishCart wishCart;

    @Autowired
    private WishReservationValidUntilSync startupSync;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        childAccountEventListener.onChildAccountChanged(new ChildAccountChangedEvent(UUID.randomUUID(), OCCURRED_AT,
            new ChildAccountChangedEvent.Data(ACCOUNT_ID, CHILD_ID, BUYER_ID, false)));
    }

    @Test
    @DisplayName("보낼 때 예약 기한 = 보낸 시각 + ttl. ttl을 줄여 기한이 지나면 보호자 주문 409, 늘리면 201 판정으로 돌아오고, "
        + "끄면 기한 없음. 기동 때 맞추기는 지금 설정(P7D)으로 되돌린다")
    void validUntilFollowsSettings() {
        String line = addToCart();
        String requestId = send(line);
        LocalDateTime sentAt = sentAt(requestId);
        assertThat(validUntil(requestId)).isEqualTo(sentAt.plusDays(7));

        // 보낸 지 이틀 된 조르기로 만든다(만료 작업은 돌지 않는다: 테스트 주기는 길다)
        jdbcTemplate.update("update orders.wish_request set created_at = created_at - interval '2 days' where id = ?",
            Long.valueOf(requestId));
        LocalDateTime twoDaysAgo = sentAt(requestId);

        // ttl 1일로 바뀌면 기한이 지난다 → 만료 작업 전이라도 결제 409
        assertThat(sync(new WishExpiryProperties(true, Duration.ofDays(1), INTERVAL, GRACE))).isOne();
        assertThat(validUntil(requestId)).isEqualTo(twoDaysAgo.plusDays(1));
        order(line).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("결제 기한이 지난 예약입니다");

        // ttl 3일이면 아직 기한 안 → 판정이 바뀐다(주문은 하지 않고 다음 단계에서)
        assertThat(sync(new WishExpiryProperties(true, Duration.ofDays(3), INTERVAL, GRACE))).isOne();
        assertThat(validUntil(requestId)).isEqualTo(twoDaysAgo.plusDays(3));
        // 같은 설정으로 다시 맞추면 바뀌는 예약이 없다
        assertThat(sync(new WishExpiryProperties(true, Duration.ofDays(3), INTERVAL, GRACE))).isZero();

        // 끄면 기한 없음
        assertThat(sync(new WishExpiryProperties(false, Duration.ofDays(1), INTERVAL, GRACE))).isOne();
        assertThat(validUntil(requestId)).isNull();

        // 기동 때 맞추기(지금 설정 P7D · 켜짐)가 다시 계산한다
        startupSync.onReady();
        assertThat(validUntil(requestId)).isEqualTo(twoDaysAgo.plusDays(7));
        order(line).expectStatus().isCreated();
    }

    @Test
    @DisplayName("기한 안의 예약 칸은 보호자가 주문하면 201")
    void orderWithinValidUntil() {
        String line = addToCart();
        send(line);

        order(line).expectStatus().isCreated();
    }

    private int sync(WishExpiryProperties properties) {
        WishReservationValidUntilService service = new WishReservationValidUntilService(wishRequestRepository,
            wishCart, new WishExpiry(properties));
        Integer changed = transactions.execute(status -> service.syncBatch(0L, 100).changed());
        return changed == null ? 0 : changed;
    }

    private LocalDateTime sentAt(String requestId) {
        return jdbcTemplate.queryForObject("select created_at from orders.wish_request where id = ?",
            LocalDateTime.class, Long.valueOf(requestId));
    }

    // 같은 예약의 허용 주체 행은 기한이 같다
    private LocalDateTime validUntil(String requestId) {
        List<LocalDateTime> values = jdbcTemplate.queryForList(
            "select distinct valid_until from orders.cart_reservation where reservation_id = ?", LocalDateTime.class,
            "wish-" + requestId);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private RestTestClient.ResponseSpec order(String cartItemId) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("optionId", "1", "childId", "1", "quantity", 1, "unitPrice", PRICE,
            "cartItemId", cartItemId)));
        body.put("shippingAddress", ADDRESS);
        return client.post().uri(ORDERS_PATH).headers(TestMembers.buyer(BUYER_ID)).body(body).exchange();
    }

    private String send(String cartItemId) {
        URI location = client.post().uri(WISH_REQUESTS_PATH).headers(child())
            .body(Map.of("cartItemIds", List.of(cartItemId))).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((WISH_REQUESTS_PATH + "/").length());
    }

    private String addToCart() {
        URI location = client.post().uri(CART_ITEMS_PATH).headers(child())
            .body(Map.of("optionId", "1", "quantity", 1)).exchange()
            .expectStatus().isCreated()
            .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        return location.getPath().substring((CART_ITEMS_PATH + "/").length());
    }

    private static Consumer<HttpHeaders> child() {
        return headers -> {
            headers.set(PrincipalHeaders.CHILD_ACCOUNT_ID, String.valueOf(ACCOUNT_ID));
            headers.set(PrincipalHeaders.CHILD_GUARDIAN_ID, String.valueOf(BUYER_ID));
        };
    }
}
