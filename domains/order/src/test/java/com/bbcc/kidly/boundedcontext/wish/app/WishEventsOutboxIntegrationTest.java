package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.dto.ApproveWishCommand;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.order.event.WishApprovedEvent;
import com.bbcc.kidly.shared.order.event.WishItemAddedEvent;
import com.bbcc.kidly.shared.order.event.WishRejectedEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조르기 이벤트 4종(#702)이 업무와 같은 트랜잭션에서 주문 아웃박스(kidly.order.events, 키 = 자녀 프로필 ID)에 한 행씩 저장되는지,
 * 실패 · 롤백이면 행도 Spring 이벤트도 없는지. 리스너는 커밋 뒤 실행되므로 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class WishEventsOutboxIntegrationTest {

    private static final Long CHILD_ID = 1L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long OPTION_A = 1L;
    private static final Long OPTION_B = 2L;
    private static final Long PRODUCT_B = 1L;
    private static final long PRICE = 25_000L;
    private static final ParentWishActor PARENT = new ParentWishActor(BUYER_ID);
    private static final TestChildWishActor CHILD = new TestChildWishActor(CHILD_ID);
    private static final ApproveWishCommand.ShippingAddress ADDRESS =
        new ApproveWishCommand.ShippingAddress("보호자", "010-0000-0000", "12345", "서울시");

    @Autowired
    private WishService wishService;

    @Autowired
    private WishRequestService wishRequestService;

    @Autowired
    private OrderFlowEventRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(PRICE, OCCURRED_AT)));
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(OPTION_B, PRICE, "ON_SALE", false, OCCURRED_AT)));
        // 자녀 1의 보호자는 BUYER_ID
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
        recorder.clear();
    }

    @Test
    @DisplayName("담기: 새 줄 · 같은 옵션 합치기마다 wish.item-added 한 행(대상 = 줄 ID, 키 = 자녀, 이번 수량), 본문 7칸 + data")
    void itemAdded() {
        Long wishItemId = wishService.add(CHILD, CHILD_ID, OPTION_A, 2).line().wishItemId();
        wishService.add(PARENT, CHILD_ID, OPTION_A, 3);

        List<WishItemAddedEvent> events = recorder.events(WishItemAddedEvent.class);
        assertThat(events).extracting(WishItemAddedEvent::data).containsExactly(
            new WishItemAddedEvent.Data(wishItemId, CHILD_ID, BUYER_ID,
                List.of(new WishItemAddedEvent.Item(PRODUCT_ID, OPTION_A, 2))),
            new WishItemAddedEvent.Data(wishItemId, CHILD_ID, BUYER_ID,
                List.of(new WishItemAddedEvent.Item(PRODUCT_ID, OPTION_A, 3))));
        List<Map<String, Object>> rows = rows("wish.item-added");
        assertThat(rows).hasSize(2);
        Map<String, Object> row = rows.getFirst();
        assertThat(row).containsEntry("event_id", events.getFirst().eventId())
            .containsEntry("topic", "kidly.order.events")
            .containsEntry("message_key", String.valueOf(CHILD_ID))
            .containsEntry("sent_at", null);
        JsonNode payload = jsonMapper.readTree((String) row.get("payload"));
        assertThat(payload.propertyNames()).containsExactly("eventId", "eventType", "schemaVersion", "occurredAt",
            "producer", "aggregateId", "correlationId", "data");
        assertThat(payload.get("eventType").asString()).isEqualTo("wish.item-added");
        assertThat(LocalDateTime.parse(payload.get("occurredAt").asString()))
            .isEqualTo(events.getFirst().occurredAt());
        assertThat(payload.get("producer").asString()).isEqualTo("order");
        assertThat(payload.get("aggregateId").asString()).isEqualTo(String.valueOf(wishItemId));
        assertThat(payload.get("correlationId").asString()).isEqualTo(String.valueOf(CHILD_ID));
        assertThat(payload.get("data").toString())
            .isEqualTo(jsonMapper.writeValueAsString(events.getFirst().data()));
    }

    @Test
    @DisplayName("보내기 · 승인(바로 결제, 일부 · 수량 변경) · 거절이 각각 한 행: 조르기 ID · 자녀 · 보호자 · items(+ 거절 사유)")
    void sentApprovedRejected() {
        Long itemA = wishService.add(CHILD, CHILD_ID, OPTION_A, 2).line().wishItemId();
        Long itemB = wishService.add(CHILD, CHILD_ID, OPTION_B, 1).line().wishItemId();
        Long approvedId = wishRequestService.send(CHILD, CHILD_ID, List.of(itemA, itemB), "사 주세요").requestId();
        wishRequestService.approve(PARENT, approvedId, new ApproveWishCommand("ORDER",
            List.of(new ApproveWishCommand.Item(itemA, 3, PRICE)), ADDRESS));
        Long rejectedId = wishRequestService.send(CHILD, CHILD_ID, List.of(itemB), null).requestId();
        wishRequestService.reject(PARENT, rejectedId, "NEXT_TIME");

        assertThat(recorder.events(WishSentEvent.class)).extracting(WishSentEvent::data).containsExactly(
            new WishSentEvent.Data(approvedId, CHILD_ID, BUYER_ID, List.of(
                new WishSentEvent.Item(PRODUCT_ID, OPTION_A, 2), new WishSentEvent.Item(PRODUCT_B, OPTION_B, 1))),
            new WishSentEvent.Data(rejectedId, CHILD_ID, BUYER_ID,
                List.of(new WishSentEvent.Item(PRODUCT_B, OPTION_B, 1))));
        assertThat(recorder.events(WishApprovedEvent.class)).extracting(WishApprovedEvent::data).containsExactly(
            new WishApprovedEvent.Data(approvedId, CHILD_ID, BUYER_ID,
                List.of(new WishApprovedEvent.Item(PRODUCT_ID, OPTION_A, 3))));
        assertThat(recorder.events(WishRejectedEvent.class)).extracting(WishRejectedEvent::data).containsExactly(
            new WishRejectedEvent.Data(rejectedId, CHILD_ID, BUYER_ID,
                List.of(new WishRejectedEvent.Item(PRODUCT_B, OPTION_B, 1)), "NEXT_TIME"));
        assertThat(jdbcTemplate.queryForList("select event_type from orders.order_outbox "
            + "where event_type like 'wish.%' and event_type <> 'wish.item-added' order by id", String.class))
            .containsExactly("wish.sent", "wish.approved", "wish.sent", "wish.rejected");
        assertThat(jdbcTemplate.queryForList("select distinct message_key from orders.order_outbox "
            + "where event_type like 'wish.%'", String.class)).containsExactly(String.valueOf(CHILD_ID));
        assertThat(aggregateIds("wish.approved")).containsExactly(String.valueOf(approvedId));
    }

    @Test
    @DisplayName("실패하면(판매 중 아님 · 잠긴 줄 · 주문 거절 롤백 · 없는 사유 · 이미 처리) 조르기 행도 Spring 이벤트도 없다")
    void nothingWhenFails() {
        Long itemA = wishService.add(CHILD, CHILD_ID, OPTION_A, 1).line().wishItemId();
        Long requestId = wishRequestService.send(CHILD, CHILD_ID, List.of(itemA), null).requestId();
        Long decidedId = wishRequestService.send(CHILD, CHILD_ID,
            List.of(wishService.add(CHILD, CHILD_ID, OPTION_B, 1).line().wishItemId()), null).requestId();
        wishRequestService.reject(PARENT, decidedId, "OTHER");
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(9L, PRICE, "STOPPED", false, OCCURRED_AT)));
        recorder.clear();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> wishService.add(CHILD, CHILD_ID, 9L, 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishService.add(CHILD, CHILD_ID, OPTION_A, 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.send(CHILD, CHILD_ID, List.of(itemA), null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.approve(PARENT, requestId, new ApproveWishCommand("ORDER",
            List.of(new ApproveWishCommand.Item(itemA, 1, PRICE - 1)), ADDRESS))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.reject(PARENT, requestId, "BORING"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wishRequestService.reject(PARENT, decidedId, "OTHER"))
            .isInstanceOf(BusinessException.class);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(recorder.events(DomainEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("거절을 감싼 트랜잭션이 롤백되면 조르기 상태도 아웃박스 행도 Spring 이벤트도 남지 않는다")
    void rollsBackWithBusiness() {
        Long itemA = wishService.add(CHILD, CHILD_ID, OPTION_A, 1).line().wishItemId();
        Long requestId = wishRequestService.send(CHILD, CHILD_ID, List.of(itemA), null).requestId();
        recorder.clear();
        jdbcTemplate.update("delete from orders.order_outbox");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            wishRequestService.reject(PARENT, requestId, "OTHER");
            throw new IllegalStateException("업무 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select status from orders.wish_request where id = ?", String.class,
            requestId)).isEqualTo("REQUESTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders.order_outbox", Long.class)).isZero();
        assertThat(recorder.events(DomainEvent.class)).isEmpty();
    }

    private List<Map<String, Object>> rows(String eventType) {
        return jdbcTemplate.queryForList("select * from orders.order_outbox where event_type = ? order by id",
            eventType);
    }

    private List<String> aggregateIds(String eventType) {
        return rows(eventType).stream()
            .map(row -> jsonMapper.readTree((String) row.get("payload")).get("aggregateId").asString())
            .toList();
    }
}
