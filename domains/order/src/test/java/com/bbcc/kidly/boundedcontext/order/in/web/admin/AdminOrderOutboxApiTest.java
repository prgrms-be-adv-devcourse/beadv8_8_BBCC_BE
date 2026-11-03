package com.bbcc.kidly.boundedcontext.order.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.out.outbox.OrderOutboxMessage;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@FunctionalTest
class AdminOrderOutboxApiTest {

    private static final String PATH = "/api/v1/admin/orders/outbox";
    private static final String SECRET = "private-payload-marker";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JpaOrderOutboxRepository jpaRepository;

    @Autowired
    private OrderOutboxRepository repository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("격리 목록 · 상세는 상태와 실패 원인만 보여 주고 본문은 노출하지 않는다")
    void listsWithoutPayload() {
        Long id = quarantined();
        client.get().uri(PATH + "/quarantined").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].outboxId").isEqualTo(id.toString())
            .jsonPath("$.content[0].status").isEqualTo("QUARANTINED")
            .jsonPath("$.content[0].payload").doesNotExist();
        client.get().uri(PATH + "/" + id).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.quarantinedAt").exists().jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("재시도는 대기 전환과 관리자 이력을 남기고 중복 요청은 1회분이다")
    void retriesOnce() {
        Long id = quarantined();
        UUID eventId = repository.findStatus(id).orElseThrow().getEventId();
        for (int i = 0; i < 2; i++) {
            client.post().uri(PATH + "/" + id + "/retry").headers(TestMembers.admin(99L))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.eventId").isEqualTo(eventId.toString())
                .jsonPath("$.lastRetriedBy").isEqualTo("99")
                .jsonPath("$.lastRetriedAt").exists()
                .jsonPath("$.retryCount").isEqualTo(1);
        }
        client.get().uri(PATH + "/quarantined").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.totalElements").isEqualTo(0);
        assertThat(jdbc.queryForObject("select payload from orders.order_outbox where id = ?", String.class, id))
            .isEqualTo(SECRET);
    }

    @Test
    @DisplayName("재시도는 로그인 정보가 필요하고 없는 ID는 404, 이미 보낸 메시지는 409다")
    void validatesRetry() {
        Long id = quarantined();
        client.post().uri(PATH + "/" + id + "/retry").exchange().expectStatus().isUnauthorized();
        client.post().uri(PATH + "/9223372036854775807/retry").headers(TestMembers.admin(99L))
            .exchange().expectStatus().isNotFound();
        transactions.executeWithoutResult(s -> repository.retryQuarantined(id, 99L, LocalDateTime.now()));
        jdbc.update("update orders.order_outbox set sent_at = now() where id = ?", id);
        client.post().uri(PATH + "/" + id + "/retry").headers(TestMembers.admin(99L))
            .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("목록 크기를 검증하고 큰 페이지는 빈 목록으로 응답한다")
    void validatesPagination() {
        quarantined();
        client.get().uri(PATH + "/quarantined?size=101").exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "/quarantined?page=-1").exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "/quarantined?page=2147483647").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.content").isEmpty();
    }

    private Long quarantined() {
        Long id = jpaRepository.save(OrderOutboxMessage.create(UUID.randomUUID(), "order.created",
            "kidly.order.events", "7", SECRET)).getId();
        transactions.executeWithoutResult(s -> repository.quarantine(id, "RecordTooLargeException",
            LocalDateTime.now()));
        return id;
    }
}
