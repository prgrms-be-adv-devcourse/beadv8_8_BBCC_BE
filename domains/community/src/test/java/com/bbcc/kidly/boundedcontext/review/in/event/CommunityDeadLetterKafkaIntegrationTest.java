package com.bbcc.kidly.boundedcontext.review.in.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.ReviewService;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.support.AsyncWait;
import com.bbcc.kidly.boundedcontext.support.KafkaIntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@KafkaIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CommunityDeadLetterKafkaIntegrationTest {

    private static final String TOPIC = "kidly.order.events";
    private static final String DLT = TOPIC + ".community.dlt";
    private static final String BREAK_ELIGIBILITY = "test_741_break_eligibility";
    private static final String BREAK_STORE = "test_741_break_dlt_store";

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private CommunityKafkaListenerTestGuard guard;

    @Autowired
    private CommunityDeadLetterService service;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private JdbcTemplate jdbc;

    private CommunityKafkaTestClient client;
    private MessageListenerContainer orderListener;
    private MessageListenerContainer dltListener;

    @BeforeEach
    void start() {
        client = new CommunityKafkaTestClient(kafka, mapper);
        orderListener = client.start(guard, CommunityOrderEventKafkaListener.ID, TOPIC);
        dltListener = client.start(guard, CommunityDeadLetterKafkaListener.ID, DLT);
        client.awaitCaughtUp("kidly.product.events.community.dlt");
    }

    @AfterEach
    void stop() {
        orderListener.stop();
        dltListener.stop();
        jdbc.execute("alter table community.review_eligibility drop constraint if exists " + BREAK_ELIGIBILITY);
        jdbc.execute("alter table community.dead_letter drop constraint if exists " + BREAK_STORE);
    }

    @Test
    @DisplayName("자격 저장 실패 → DLT DB 보관 → 장애 해소 → 관리자 재처리 → 리뷰 작성까지 복구한다")
    void recoversAfterStorageFailure() {
        long itemId = ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
        var event = confirmed(itemId);
        jdbc.execute("alter table community.review_eligibility add constraint " + BREAK_ELIGIBILITY
            + " check (order_item_id <> " + itemId + ") not valid");
        var sent = client.send(TOPIC, event);
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT, () -> count(event.eventId().toString()) == 1);
        Long id = id(event.eventId().toString());
        assertThat(service.get(id).getSourceTopic()).isEqualTo(TOPIC);
        assertThat(service.get(id).getSourceOffset()).isEqualTo(sent.offset());
        assertThat(service.get(id).getState()).isEqualTo(State.OPEN);
        jdbc.execute("alter table community.review_eligibility drop constraint " + BREAK_ELIGIBILITY);

        assertThat(service.reprocess(id, 99L).getState()).isEqualTo(State.REPROCESSED);
        assertThat(service.reprocess(id, 99L).getReplayCount()).isOne();
        assertThat(jdbc.queryForObject("select count(*) from community.review_eligibility where order_item_id = ?",
            Long.class, itemId)).isOne();
        assertThat(reviews.write(13L, "작성자", Long.toString(itemId), 5, "정상 구매 후기").orderItemId())
            .isEqualTo(itemId);
    }

    @Test
    @DisplayName("DLT DB 저장 실패는 완료로 넘기지 않고 재시도하며 원본과 예외 메시지는 로그에 남기지 않는다")
    void retriesDltStorageUntilRecovered(CapturedOutput output) {
        String eventId = UUID.randomUUID().toString();
        String marker = "private-dlt-body-741";
        jdbc.execute("alter table community.dead_letter add constraint " + BREAK_STORE
            + " check (event_id <> '" + eventId + "') not valid");
        var sent = client.send(DLT, "1", "order-item.purchase-confirmed", eventId, marker + (char) 0);
        AsyncWait.until(CommunityKafkaTestClient.TIMEOUT,
            () -> output.getOut().contains("커뮤니티 DLT 보관 실패"));
        assertThat(count(eventId)).isZero();
        jdbc.execute("alter table community.dead_letter drop constraint " + BREAK_STORE);
        client.awaitProcessed(sent);
        assertThat(count(eventId)).isOne();
        assertThat(service.get(id(eventId)).getSourceTopic()).isEqualTo(TOPIC);
        assertThat(output.getOut()).doesNotContain(marker);
    }

    private long count(String eventId) {
        return jdbc.queryForObject("select count(*) from community.dead_letter where event_id = ?",
            Long.class, eventId);
    }

    private Long id(String eventId) {
        return jdbc.queryForObject("select id from community.dead_letter where event_id = ?", Long.class, eventId);
    }

    private static OrderItemPurchaseConfirmedEvent confirmed(long itemId) {
        LocalDateTime at = LocalDateTime.now().minusDays(5);
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), at,
            new OrderItemPurchaseConfirmedEvent.Data(itemId, itemId / 10, 13L, 2L, null, 3L, 4L, "110", 1, 1000L, at));
    }
}
