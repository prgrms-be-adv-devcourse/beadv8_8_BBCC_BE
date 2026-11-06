package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.SellerDeadLetterService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.member.support.security.TestAdmins;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@FunctionalTest
class AdminSellerDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/seller-dead-letters";
    private static final String TOPIC = "kidly.member.events";
    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerDeadLetterService service;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("기본 OPEN 목록과 상세는 원본 본문을 응답하지 않는다")
    void listsWithoutPayload() {
        var id = receive(1L, "member.changed", "private-body");

        client.get().uri(PATH).headers(TestAdmins.of(ADMIN_ID)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content[0].deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.content[0].status").isEqualTo("OPEN")
            .jsonPath("$.content[0].sourceTopic").isEqualTo(TOPIC)
            .jsonPath("$.content[0].eventType").isEqualTo("member.changed")
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
    @DisplayName("재처리하면 원래 처리기가 신청 닉네임을 바꾸고 REPROCESSED가 되며, 다시 불러도 재실행하지 않는다")
    void reprocesses() {
        var applicationId = sellerApplicationRepository.save(SellerFixture.application(MEMBER_ID)).getId();
        var id = receive(1L, "member.changed", memberChanged("하은맘"));

        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo(ADMIN_ID.toString())
            .jsonPath("$.lastReplayFailureType").doesNotExist()
            .jsonPath("$.payload").doesNotExist();
        assertThat(nickname(applicationId)).isEqualTo("하은맘");

        jdbc.update("update seller.seller_application set applicant_nickname = '다른닉',"
            + " applicant_nickname_changed_at = null where id = ?", applicationId);
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(ADMIN_ID))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("REPROCESSED")
            .jsonPath("$.replayCount").isEqualTo(1);
        assertThat(nickname(applicationId)).isEqualTo("다른닉");
    }

    @Test
    @DisplayName("처리기가 실패하면 200/OPEN으로 실패 이력을 돌려준다")
    void reportsReplayFailure() {
        var id = receive(1L, "member.changed", "{\"data\":{\"memberId\":\"숫자 아님\"}}");

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
        var id = receive(1L, "member.changed", "private-body");

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

    private Long receive(long dltOffset, String eventType, String payload) {
        var eventId = UUID.randomUUID().toString();
        service.receive(new SellerDeadLetterMessage("kidly.member.events.seller.dlt", 0, dltOffset, TOPIC, 0, 1L,
            String.valueOf(MEMBER_ID), eventType, eventId, payload, "tools.jackson.core.JacksonException"));
        return jdbc.queryForObject("select id from seller.dead_letter where event_id = ?", Long.class, eventId);
    }

    private String nickname(Long applicationId) {
        return sellerApplicationRepository.getById(applicationId).getApplicantNickname();
    }

    private String memberChanged(String nickname) {
        return jsonMapper.writeValueAsString(new MemberChangedEvent(UUID.randomUUID(), SellerFixture.NOW,
            new MemberChangedEvent.Data(MEMBER_ID, nickname)));
    }
}
