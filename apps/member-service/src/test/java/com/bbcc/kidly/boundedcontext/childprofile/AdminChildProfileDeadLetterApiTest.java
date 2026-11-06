package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileDeadLetterService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@FunctionalTest
class AdminChildProfileDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/child-profile-dead-letters";
    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String AUTH_TOPIC = "kidly.auth.events";
    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 1L;
    private static final Long ACCOUNT_ID = 7L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private ChildProfileDeadLetterService service;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private GuardianVerificationRepository guardianVerificationRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("기본 OPEN 목록과 상세는 원본 본문을 응답하지 않는다")
    void listsWithoutPayload() {
        var id = receive(MEMBER_TOPIC, 1L, "member.withdrawn", "private-body");

        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content[0].deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.content[0].status").isEqualTo("OPEN")
            .jsonPath("$.content[0].sourceTopic").isEqualTo(MEMBER_TOPIC)
            .jsonPath("$.content[0].eventType").isEqualTo("member.withdrawn")
            .jsonPath("$.content[0].payload").doesNotExist()
            .jsonPath("$.totalElements").isEqualTo(1);
        client.get().uri(PATH + "?status=REPROCESSED").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.content").isEmpty();
        client.get().uri(PATH + "/" + id).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("회원 탈퇴를 재처리하면 원래 처리기가 자녀와 본인인증 기록을 지우고 REPROCESSED가 되며, 다시 불러도 재실행하지 않는다")
    void reprocessesMemberWithdrawn() {
        childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));
        guardianVerificationRepository.save(GuardianVerificationFixture.verification(MEMBER_ID));
        var id = receive(MEMBER_TOPIC, 1L, "member.withdrawn", jsonMapper.writeValueAsString(
            new MemberWithdrawnEvent(UUID.randomUUID(), ChildProfileFixture.NOW,
                new MemberWithdrawnEvent.Data(MEMBER_ID))));

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").doesNotExist()
            .jsonPath("$.payload").doesNotExist();
        assertThat(childProfileRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
        assertThat(guardianVerificationRepository.existsByMemberId(MEMBER_ID)).isFalse();

        childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1);
        assertThat(childProfileRepository.findAllByMemberId(MEMBER_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("자녀 계정 변경을 재처리하면 원래 처리기가 계정 연결을 만든다")
    void reprocessesChildAccountChange() {
        var id = receive(AUTH_TOPIC, 1L, "child-account.changed", jsonMapper.writeValueAsString(
            new ChildAccountChangedEvent(UUID.randomUUID(), ChildProfileFixture.NOW,
                new ChildAccountChangedEvent.Data(ACCOUNT_ID, 42L, MEMBER_ID, false))));

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.sourceTopic").isEqualTo(AUTH_TOPIC);
        assertThat(jdbc.queryForObject(
            "select child_profile_id from childprofile.child_account_link where account_id = ? and deleted_at is null",
            Long.class, ACCOUNT_ID)).isEqualTo(42L);
    }

    @Test
    @DisplayName("처리기가 실패하면 200/OPEN으로 실패 이력을 돌려준다")
    void reportsReplayFailure() {
        var id = receive(MEMBER_TOPIC, 1L, "member.withdrawn", "{\"data\":{\"memberId\":\"숫자 아님\"}}");

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").exists()
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("인증 · 없는 기록 · 상태 · 페이지 범위를 검증한다")
    void validatesRequests() {
        var id = receive(MEMBER_TOPIC, 1L, "member.withdrawn", "private-body");

        client.post().uri(PATH + "/" + id + "/reprocess").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH + "/9223372036854775807").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNotFound();
        client.post().uri(PATH + "/9223372036854775807/reprocess").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isNotFound();
        client.get().uri(PATH + "?status=UNKNOWN").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?size=101").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=-1").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=2147483647").headers(TestAdmins.of(ADMIN_ID)).exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.content").isEmpty();
    }

    private Long receive(String sourceTopic, long dltOffset, String eventType, String payload) {
        var eventId = UUID.randomUUID().toString();
        service.receive(new ChildProfileDeadLetterMessage(sourceTopic + ".childprofile.dlt", 0, dltOffset, sourceTopic,
            0, 1L, String.valueOf(MEMBER_ID), eventType, eventId, payload, "tools.jackson.core.JacksonException"));
        return jdbc.queryForObject("select id from childprofile.dead_letter where event_id = ?", Long.class, eventId);
    }
}
