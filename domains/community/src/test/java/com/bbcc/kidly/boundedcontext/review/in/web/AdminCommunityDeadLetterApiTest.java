package com.bbcc.kidly.boundedcontext.review.in.web;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.app.port.CommunityDeadLetters.Received;
import com.bbcc.kidly.boundedcontext.support.FunctionalTest;
import com.bbcc.kidly.global.security.TestMembers;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminCommunityDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/community/dead-letters";

    @Autowired
    private RestTestClient client;

    @Autowired
    private CommunityDeadLetterService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("기본 OPEN 목록과 상세는 원본을 응답하지 않고 실패 결과와 관리자를 확인할 수 있다")
    void listsAndReportsFailureWithoutPayload() {
        Long id = receive();
        client.get().uri(PATH).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content[0].deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.content[0].payload").doesNotExist();
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestMembers.admin(99L))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo("99")
            .jsonPath("$.lastReplayFailureType").exists()
            .jsonPath("$.payload").doesNotExist();
        client.get().uri(PATH + "/" + id).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("인증 · 없는 기록 · 상태 · 페이지 범위를 검증한다")
    void validatesRequests() {
        Long id = receive();
        client.post().uri(PATH + "/" + id + "/reprocess").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH + "/9223372036854775807").exchange().expectStatus().isNotFound();
        client.get().uri(PATH + "?status=UNKNOWN").exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "?size=101").exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=-1").exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=2147483647").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content").isEmpty();
    }

    private Long receive() {
        service.receive(new Received("kidly.order.events.community.dlt", 0, 1L, "kidly.order.events", 0, 1L,
            "1", "order-item.purchase-confirmed", UUID.randomUUID().toString(), "private-body", "JsonException"));
        return jdbc.queryForObject("select id from community.dead_letter", Long.class);
    }
}
