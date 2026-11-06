package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.AuthDeadLetterService;
import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.out.JpaChildProfileReplicaRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminAuthDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/auth-dead-letters";
    private static final String MEMBER_TOPIC = "kidly.member.events";
    private static final String CHILD_PROFILE_TOPIC = "kidly.childprofile.events";
    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private static final Long CHILD_ID = 11L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private AuthDeadLetterService service;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private JpaChildProfileReplicaRepository childProfileReplicaRepository;

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
    @DisplayName("탈퇴 이벤트를 재처리하면 원래 처리기가 소셜 연결을 지우고 REPROCESSED가 되며, 다시 불러도 재실행하지 않는다")
    void reprocessesMemberWithdrawn() {
        socialAccountRepository.save(SocialAccountFixture.linked(MEMBER_ID, UUID.randomUUID().toString()));
        var id = receive(MEMBER_TOPIC, 1L, "member.withdrawn", "{\"data\":{\"memberId\":" + MEMBER_ID + "}}");

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").doesNotExist()
            .jsonPath("$.payload").doesNotExist();
        assertThat(socialAccountRepository.findByMemberId(MEMBER_ID)).isEmpty();

        socialAccountRepository.save(SocialAccountFixture.linked(MEMBER_ID, UUID.randomUUID().toString()));
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1);
        assertThat(socialAccountRepository.findByMemberId(MEMBER_ID)).isNotEmpty();
    }

    @Test
    @DisplayName("자녀 프로필 이벤트를 재처리하면 원래 처리기가 자녀 프로필 복사본을 만든다")
    void reprocessesChildChanged() {
        var id = receive(CHILD_PROFILE_TOPIC, 1L, "child.changed",
            "{\"data\":{\"childId\":" + CHILD_ID + ",\"memberId\":" + MEMBER_ID + ",\"deleted\":false}}");

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED");
        assertThat(childProfileReplicaRepository.findById(CHILD_ID)).get()
            .satisfies(replica -> assertThat(replica.getGuardianId()).isEqualTo(MEMBER_ID));
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
        service.receive(new AuthDeadLetterMessage(sourceTopic + ".auth.dlt", 0, dltOffset, sourceTopic, 0, 1L,
            String.valueOf(MEMBER_ID), eventType, eventId, payload, "tools.jackson.core.JacksonException"));
        return jdbc.queryForObject("select id from auth.dead_letter where event_id = ?", Long.class, eventId);
    }
}
