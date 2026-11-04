package com.bbcc.kidly.boundedcontext.settlement;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementDeadLetterService;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementDeadLetters.Received;
import com.bbcc.kidly.global.security.TestAdmins;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminSettlementDeadLetterApiTest {

    private static final String PATH = "/api/v1/admin/settlement-dead-letters";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SettlementDeadLetterService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("기본 OPEN 목록과 상세는 원본을 응답하지 않고, 재처리 실패 결과와 관리자를 확인할 수 있다")
    void listsAndReportsFailureWithoutPayload() {
        Long id = receive();
        client.get().uri(PATH).headers(TestAdmins.of(99L)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.content[0].deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.content[0].status").isEqualTo("OPEN")
            .jsonPath("$.content[0].sourceTopic").isEqualTo("kidly.payment.events")
            .jsonPath("$.content[0].payload").doesNotExist()
            .jsonPath("$.totalElements").isEqualTo(1);
        // 본문이 정산서 형식이 아니라 처리기가 실패한다. 200/OPEN으로 실패 이력을 돌려준다
        client.post().uri(PATH + "/" + id + "/reprocess").headers(TestAdmins.of(99L))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.status").isEqualTo("OPEN")
            .jsonPath("$.replayCount").isEqualTo(1)
            .jsonPath("$.lastReplayedBy").isEqualTo("99")
            .jsonPath("$.lastReplayFailureType").exists()
            .jsonPath("$.payload").doesNotExist();
        client.get().uri(PATH + "/" + id).headers(TestAdmins.of(99L)).exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.deadLetterId").isEqualTo(id.toString())
            .jsonPath("$.payload").doesNotExist();
    }

    @Test
    @DisplayName("인증 · 없는 기록 · 상태 · 페이지 범위를 검증한다")
    void validatesRequests() {
        Long id = receive();
        client.post().uri(PATH + "/" + id + "/reprocess").exchange().expectStatus().isUnauthorized();
        client.get().uri(PATH + "/9223372036854775807").headers(TestAdmins.of(99L)).exchange()
            .expectStatus().isNotFound();
        client.get().uri(PATH + "?status=UNKNOWN").headers(TestAdmins.of(99L)).exchange()
            .expectStatus().isBadRequest();
        client.get().uri(PATH + "?size=101").headers(TestAdmins.of(99L)).exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=-1").headers(TestAdmins.of(99L)).exchange().expectStatus().isBadRequest();
        client.get().uri(PATH + "?page=2147483647").headers(TestAdmins.of(99L)).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.content").isEmpty();
    }

    private Long receive() {
        service.receive(new Received("kidly.payment.events.settlement.dlt", 0, 1L, "kidly.payment.events", 0, 1L,
            "1", "payment.payout-completed", UUID.randomUUID().toString(), "private-body",
            "tools.jackson.core.JacksonException"));
        return jdbc.queryForObject("select id from settlement.settlement_dead_letter", Long.class);
    }
}
