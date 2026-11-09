package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberWithdrawalService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationDeadLetterService;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.State;
import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationDeadLetters.Summary;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @DisplayName("탈퇴 뒤에 보관된 가입 DLT를 재처리해도 탈퇴 표시로 건너뛰어 복사본 · 메일 · 알림함이 생기지 않는다")
    void skipsRegistrationOfWithdrawnMember() {
        memberWithdrawalService.purge(MEMBER_ID);
        memberWithdrawalService.purge(MEMBER_ID);  // 같은 탈퇴를 다시 받아도 표시는 한 행
        Long id = receive(MEMBER_ID, 1L);

        Summary replayed = deadLetterService.reprocess(id, ADMIN_ID);

        assertThat(replayed.getState()).isEqualTo(State.REPROCESSED);
        assertNothingFor(MEMBER_ID);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from notification.withdrawn_member where member_id = ?", Long.class, MEMBER_ID))
            .isOne();
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
