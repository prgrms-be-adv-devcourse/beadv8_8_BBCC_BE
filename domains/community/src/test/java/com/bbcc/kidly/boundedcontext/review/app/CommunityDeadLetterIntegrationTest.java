package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.State;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CommunityDeadLetterIntegrationTest {

    private static final String BREAK_RESULT = "test_break_community_replay_result";

    @Autowired
    private CommunityDeadLetterService service;

    @Autowired
    private CommunityDeadLetters repository;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper mapper;

    @AfterEach
    void restore() {
        jdbc.execute("alter table community.dead_letter drop constraint if exists " + BREAK_RESULT);
    }

    @Test
    @DisplayName("같은 DLT 위치는 한 건만 보관하고 재처리 뒤 실제 리뷰를 쓸 수 있다")
    void recoversEligibilityAndReviewOnce() {
        Received message = message(confirmed(7L), 1L);
        assertThat(service.receive(message)).isTrue();
        assertThat(service.receive(message)).isFalse();
        Long id = id();
        assertThat(service.reprocess(id, 99L).getState()).isEqualTo(State.REPROCESSED);
        assertThat(service.reprocess(id, 99L).getReplayCount()).isOne();
        assertThat(service.receive(message)).isFalse();
        assertThat(service.get(id).getState()).isEqualTo(State.REPROCESSED);
        assertThat(countEligibility()).isOne();
        assertThat(reviews.write(13L, "작성자", "7", 5, "구매 후기").orderItemId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("제어문자가 있는 깨진 원본도 정확히 보존하며 재처리 실패를 성공으로 표시하지 않는다")
    void preservesMalformedBody() {
        String payload = "private-body" + (char) 0 + "broken-json";
        service.receive(new Received("kidly.order.events.community.dlt", 0, 2L, "kidly.order.events", null, null,
            "1", "order-item.purchase-confirmed", UUID.randomUUID().toString(), payload, "JacksonException"));
        Long id = id();
        assertThat(repository.findForReplay(id).orElseThrow().message().payload()).isEqualTo(payload);
        var result = service.reprocess(id, 99L);
        assertThat(result.getState()).isEqualTo(State.OPEN);
        assertThat(result.getReplayCount()).isOne();
        assertThat(result.getLastReplayFailureType()).isNotBlank();
        assertThat(countEligibility()).isZero();
    }

    @Test
    @DisplayName("필수값 누락으로 무시되는 메시지는 재처리 성공이 아니다")
    void invalidRequiredFieldsRemainOpen() {
        var event = confirmed(7L);
        var data = event.data();
        var invalid = new OrderItemPurchaseConfirmedEvent(event.eventId(), event.occurredAt(),
            new OrderItemPurchaseConfirmedEvent.Data(7L, 1L, null, 2L, null, data.productId(), data.optionId(),
                data.size(), 1, 1000L, data.confirmedAt()));
        service.receive(message(invalid, 3L));
        assertThat(service.reprocess(id(), 99L).getState()).isEqualTo(State.OPEN);
        assertThat(countEligibility()).isZero();
    }

    @Test
    @DisplayName("동시 재처리도 자격과 성공 이력은 한 번으로 수렴한다")
    void concurrentReplayConverges() throws Exception {
        service.receive(message(confirmed(7L), 4L));
        Long id = id();
        CompletableFuture.allOf(CompletableFuture.runAsync(() -> service.reprocess(id, 99L)),
            CompletableFuture.runAsync(() -> service.reprocess(id, 99L))).get(15, TimeUnit.SECONDS);
        assertThat(countEligibility()).isOne();
        assertThat(service.get(id).getState()).isEqualTo(State.REPROCESSED);
        assertThat(service.get(id).getReplayCount()).isOne();
    }

    @Test
    @DisplayName("반영 후 결과 저장이 실패해도 다음 재처리로 자격을 중복 생성하지 않고 복구한다")
    void resultFailureAfterBusinessCommitIsRecoverable() {
        service.receive(message(confirmed(7L), 5L));
        Long id = id();
        jdbc.execute("alter table community.dead_letter add constraint " + BREAK_RESULT
            + " check (state <> 'REPROCESSED') not valid");
        assertThatThrownBy(() -> service.reprocess(id, 99L)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countEligibility()).isOne();
        assertThat(service.get(id).getState()).isEqualTo(State.OPEN);
        restore();
        assertThat(service.reprocess(id, 99L).getState()).isEqualTo(State.REPROCESSED);
        assertThat(countEligibility()).isOne();
    }

    @Test
    @DisplayName("늦게 기록한 실패가 이미 성공한 상태를 되돌리지 않는다")
    void lateFailureDoesNotOverwriteSuccess() {
        service.receive(message(confirmed(7L), 6L));
        Long id = id();
        service.reprocess(id, 99L);
        int changed = transactions.execute(s -> repository.recordReplayFailure(id, 100L, LocalDateTime.now(), "Late"));
        assertThat(changed).isZero();
        assertThat(service.get(id).getState()).isEqualTo(State.REPROCESSED);
        assertThat(service.get(id).getLastReplayFailureType()).isNull();
        assertThat(service.get(id).getLastReplayedBy()).isEqualTo(99L);
    }

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DisplayName("상품 DLT도 원래 상품 처리기로 복구한다")
    void replaysProductMessage() {
        LocalDateTime at = LocalDateTime.now().minusHours(1);
        var event = new ProductChangedEvent(UUID.randomUUID(), at,
            new ProductChangedEvent.Data(3L, 2L, "복구 상품", 5L, ProductStatus.ON_SALE, null,
                at.minusDays(1), false, List.of()));
        service.receive(new Received("kidly.product.events.community.dlt", 0, 7L, "kidly.product.events", 0, 7L,
            "3", event.eventType(), event.eventId().toString(), mapper.writeValueAsString(event), "DatabaseException"));
        assertThat(service.reprocess(id(), 99L).getState()).isEqualTo(State.REPROCESSED);
        assertThat(jdbc.queryForObject("select name from community.replica_product where product_id = 3", String.class))
            .isEqualTo("복구 상품");
    }

    private Long id() {
        return jdbc.queryForObject("select id from community.dead_letter", Long.class);
    }

    private long countEligibility() {
        return jdbc.queryForObject("select count(*) from community.review_eligibility", Long.class);
    }

    private Received message(OrderItemPurchaseConfirmedEvent event, long offset) {
        return new Received("kidly.order.events.community.dlt", 0, offset, "kidly.order.events", 0, offset,
            "1", event.eventType(), event.eventId().toString(), mapper.writeValueAsString(event), "DatabaseException");
    }

    private static OrderItemPurchaseConfirmedEvent confirmed(Long itemId) {
        LocalDateTime at = LocalDateTime.now().minusDays(2);
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), at,
            new OrderItemPurchaseConfirmedEvent.Data(itemId, 1L, 13L, 2L, null, 3L, 4L, "110", 1, 1000L, at));
    }
}
