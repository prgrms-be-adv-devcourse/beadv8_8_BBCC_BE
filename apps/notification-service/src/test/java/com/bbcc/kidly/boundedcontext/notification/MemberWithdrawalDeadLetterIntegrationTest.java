package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberWithdrawalService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가입(member.registered)이 DLT로 간 사이 탈퇴가 처리된 뒤 관리자가 그 DLT를 재처리해도
 * 탈퇴한 회원의 이메일 복사본 · 환영 메일 · 알림함이 다시 생기지 않는다.
 */
@IntegrationTest
class MemberWithdrawalDeadLetterIntegrationTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;
    private static final long ADMIN_ID = 99L;
    private static final String MEMBER_EVENTS = "kidly.member.events";

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

    @Autowired
    private NotificationDeadLetterService deadLetterService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("탈퇴하면 그 회원의 회원 토픽 DLT 본문을 비우고, 재처리하면 실패로 기록되며 복사본 · 메일 · 알림함이 생기지 않는다")
    void erasesPayloadOnWithdrawal() {
        Long id = receive(MEMBER_ID, 1L);
        Long otherId = receive(OTHER_MEMBER_ID, 2L);

        memberWithdrawalService.purge(MEMBER_ID);

        assertThat(payload(id)).isNull();
        assertThat(payload(otherId)).isNotNull();
        Summary replayed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(replayed.getState()).isEqualTo(State.OPEN);
        assertThat(replayed.getLastReplayFailureType()).isNotNull();
        assertNothingFor(MEMBER_ID);
    }

    @Test
    @DisplayName("본문이 남은 가입 DLT를 탈퇴 뒤에 재처리해도 탈퇴 표시로 건너뛰어 복사본 · 메일 · 알림함이 생기지 않는다")
    void skipsRegistrationOfWithdrawnMember() {
        Long id = receive(MEMBER_ID, 1L);
        byte[] body = payload(id);
        memberWithdrawalService.purge(MEMBER_ID);
        memberWithdrawalService.purge(MEMBER_ID);  // 같은 탈퇴를 다시 받아도 표시는 한 행
        // 본문 비우기를 거치지 않은 행(이 수정 전에 보관된 것 등)을 흉내 낸다
        jdbcTemplate.update("update notification.dead_letter set payload = ? where id = ?", body, id);

        Summary replayed = deadLetterService.reprocess(id, ADMIN_ID);

        assertThat(replayed.getState()).isEqualTo(State.REPROCESSED);
        assertNothingFor(MEMBER_ID);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification.withdrawn_member where member_id = ?", Long.class, MEMBER_ID))
            .isOne();
    }

    @Test
    @DisplayName("탈퇴 뒤에 보관되는 그 회원의 회원 토픽 DLT는 본문 없이 저장하고, 다른 회원 것은 본문을 둔다")
    void storesWithoutPayloadAfterWithdrawal() {
        memberWithdrawalService.purge(MEMBER_ID);

        Long id = receive(MEMBER_ID, 1L);
        Long otherId = receive(OTHER_MEMBER_ID, 2L);

        assertThat(payload(id)).isNull();
        assertThat(payload(otherId)).isNotNull();
        Summary replayed = deadLetterService.reprocess(id, ADMIN_ID);
        assertThat(replayed.getState()).isEqualTo(State.OPEN);
        assertNothingFor(MEMBER_ID);
    }

    @Test
    @DisplayName("본문이 없는 회원 토픽 DLT(tombstone 등)도 보관한다")
    void storesMessageWithoutPayload() {
        deadLetterService.receive(new Received(MEMBER_EVENTS + ".notification.dlt", 0, 3L, MEMBER_EVENTS, 0, 3L,
            OTHER_MEMBER_ID.toString(), null, null, null, IllegalStateException.class.getName()));

        Long id = jdbcTemplate.queryForObject("select id from notification.dead_letter where dlt_offset = 3",
            Long.class);
        assertThat(payload(id)).isNull();
    }

    @Test
    @DisplayName("탈퇴 처리와 가입 처리는 같은 회원 잠금을 트랜잭션이 끝날 때까지 쥔다")
    void holdsMemberLockUntilCommit() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            memberWithdrawalService.purge(MEMBER_ID);
            assertThat(lockedElsewhere(MEMBER_ID)).isTrue();
            assertThat(lockedElsewhere(OTHER_MEMBER_ID)).isFalse();
        });
        transaction.executeWithoutResult(status -> {
            assertThat(memberWithdrawalService.lockAndCheckWithdrawn(MEMBER_ID)).isTrue();
            assertThat(lockedElsewhere(MEMBER_ID)).isTrue();
        });
        assertThat(lockedElsewhere(MEMBER_ID)).isFalse();
    }

    // 트랜잭션에 묶이지 않은 다른 연결에서 같은 키를 잡아 본다. 못 잡으면 잠겨 있다(autocommit이라 잡았으면 바로 풀린다)
    private boolean lockedElsewhere(Long memberId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "select pg_try_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, "kidly.notification.member-withdrawal:" + memberId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return !result.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertNothingFor(Long memberId) {
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification.member_contact where member_id = ?", Long.class, memberId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification.notification", Long.class))
            .isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification.inbox where recipient_id = ?", Long.class, memberId)).isZero();
    }

    // 회원이 보내는 형식 그대로(공통 칸 7개 + data) DLT에 보관된 것으로 만든다
    private Long receive(Long memberId, long dltOffset) {
        var event = new MemberRegisteredEvent(UUID.randomUUID(), LocalDateTime.now(),
            new MemberRegisteredEvent.Data(memberId, "member-" + memberId + "@kidly.co.kr"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", event.eventId());
        body.put("eventType", event.eventType());
        body.put("schemaVersion", event.schemaVersion());
        body.put("occurredAt", event.occurredAt());
        body.put("producer", event.producer());
        body.put("aggregateId", event.aggregateId());
        body.put("correlationId", event.correlationId());
        body.put("data", event.data());
        deadLetterService.receive(new Received(MEMBER_EVENTS + ".notification.dlt", 0, dltOffset, MEMBER_EVENTS, 0,
            dltOffset, event.correlationId(), event.eventType(), event.eventId().toString(),
            jsonMapper.writeValueAsString(body), IllegalStateException.class.getName()));
        return jdbcTemplate.queryForObject("select id from notification.dead_letter where dlt_offset = ?",
            Long.class, dltOffset);
    }

    private byte[] payload(Long id) {
        return jdbcTemplate.queryForObject("select payload from notification.dead_letter where id = ?", byte[].class,
            id);
    }
}
