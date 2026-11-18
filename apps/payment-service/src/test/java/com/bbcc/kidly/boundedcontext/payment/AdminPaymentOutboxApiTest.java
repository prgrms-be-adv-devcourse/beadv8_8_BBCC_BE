package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 격리된 결제 이벤트는 같은 키의 뒤 메시지와 함께 릴레이 대상에서 빠지고, 관리자가 풀면 순서대로 다시 대상이 된다.
 */
@FunctionalTest
class AdminPaymentOutboxApiTest {

    private static final String PATH = "/api/v1/admin/payments/parked-events";
    private static final Long ADMIN_ID = 3L;
    private static final int MAX_ATTEMPTS = 30;

    @Autowired
    private RestTestClient client;

    @Autowired
    private PaymentOutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("격리된 메시지와 같은 키의 뒤 메시지는 보내지 않고, 다른 키는 보낸다. 관리자가 풀면 격리된 메시지부터 다시 보낸다")
    void parkAndRelease() {
        long parked = insert("order-1", true);
        long sameKey = insert("order-1", false);
        long otherKey = insert("order-2", false);

        assertThat(unsentIds()).containsExactly(otherKey);
        assertThat(outboxRepository.countParked()).isOne();

        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0].outboxId").isEqualTo(parked)
            .jsonPath("$.content[0].messageKey").isEqualTo("order-1")
            .jsonPath("$.content[0].attemptCount").isEqualTo(30)
            .jsonPath("$.content[0].lastError").isEqualTo("RecordTooLargeException")
            .jsonPath("$.content[0].payload").doesNotExist();

        release(parked).expectStatus().isOk()
            .expectBody().jsonPath("$.outboxId").isEqualTo(parked);

        assertThat(unsentIds()).containsExactly(parked, sameKey, otherKey);
        assertThat(jdbcTemplate.queryForObject(
            "select attempt_count from payment.payment_outbox where id = ?", Integer.class, parked)).isZero();
        release(parked).expectStatus().isEqualTo(409);
        release(-1L).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("시도 상한에 닿은 메시지는 주되 그 키의 뒤 메시지는 빼서, 뒤 메시지가 읽는 건수를 다 채워 다른 키를 막지 않는다")
    void exhaustedKeyDoesNotFillBatch() {
        long exhausted = insert("order-1", MAX_ATTEMPTS, false);
        insert("order-1", 0, false);
        insert("order-1", 0, false);
        long otherKey = insert("order-2", 0, false);

        assertThat(outboxRepository.findUnsent(2, MAX_ATTEMPTS)).extracting(PaymentOutboxMessage::getId)
            .containsExactly(exhausted, otherKey);
    }

    private RestTestClient.ResponseSpec release(long outboxId) {
        return client.post().uri(PATH + "/" + outboxId + "/release").headers(TestAdmins.of(ADMIN_ID)).exchange();
    }

    private java.util.List<Long> unsentIds() {
        return outboxRepository.findUnsent(100, MAX_ATTEMPTS).stream().map(PaymentOutboxMessage::getId).toList();
    }

    private long insert(String key, boolean parked) {
        return insert(key, parked ? MAX_ATTEMPTS : 0, parked);
    }

    private long insert(String key, int attemptCount, boolean parked) {
        return jdbcTemplate.queryForObject("""
            insert into payment.payment_outbox (event_id, event_type, topic, message_key, payload, attempt_count,
                last_error, parked_at, created_at, updated_at)
            values (?, 'payment.completed', 'kidly.payment.events', ?, '{}', ?, ?, ?, now(), now())
            returning id
            """, Long.class, UUID.randomUUID(), key, attemptCount, attemptCount > 0 ? "RecordTooLargeException" : null,
            parked ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()) : null);
    }
}
