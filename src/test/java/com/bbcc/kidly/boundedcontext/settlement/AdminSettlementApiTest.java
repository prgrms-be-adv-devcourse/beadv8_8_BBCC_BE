package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunRequest;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementPeriod;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementRunResponse;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminSettlementApiTest {

    private static final String RUN_PATH = "/api/v1/admin/settlements/run";
    private static final String RUNS_PATH = "/api/v1/admin/settlement-runs";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Test
    @DisplayName("관리자가 기준일로 정산을 실행하면 201과 Location, 실행 기록을 준다")
    void run() {
        var cutoffOn = LocalDate.now(SettlementPeriod.SERVICE_ZONE).withDayOfMonth(1);

        var result = run(cutoffOn).expectStatus().isCreated()
            .expectHeader().value("Location", location -> assertThat(location).startsWith(RUNS_PATH + "/"))
            .expectBody(SettlementRunResponse.class)
            .returnResult();

        var body = result.getResponseBody();
        assertThat(body.status()).isEqualTo("COMPLETED");
        assertThat(body.triggerType()).isEqualTo("MANUAL");
        assertThat(body.periodStartOn()).isEqualTo(cutoffOn.minusDays(1).withDayOfMonth(1));
        assertThat(body.periodEndOn()).isEqualTo(cutoffOn.minusDays(1));
        assertThat(body.successCount()).isZero();
        assertThat(body.requestedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.getResponseHeaders().getLocation().getPath()).isEqualTo(RUNS_PATH + "/" + body.runId());
    }

    @Test
    @DisplayName("기준일이 없으면 400이다")
    void runMissingCutoff() {
        client.post().uri(RUN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("미래 기준일이면 400이다")
    void runFutureCutoff() {
        run(LocalDate.now(SettlementPeriod.SERVICE_ZONE).plusDays(1)).expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo("정산 기준일이 올바르지 않습니다");
    }

    @Test
    @DisplayName("이미 정산한 기준일이면 409이다")
    void runAlreadyRun() {
        var cutoffOn = LocalDate.now(SettlementPeriod.SERVICE_ZONE).withDayOfMonth(1);
        run(cutoffOn).expectStatus().isCreated();

        run(cutoffOn).expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("판매자는 실행할 수 없다. 403이다")
    void runForbidden() {
        client.post().uri(RUN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.seller(2L)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SettlementRunRequest(LocalDate.now(SettlementPeriod.SERVICE_ZONE).withDayOfMonth(1)))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("실행 기록을 id로 조회한다")
    void getRun() {
        var created = run(LocalDate.now(SettlementPeriod.SERVICE_ZONE).withDayOfMonth(1)).expectStatus().isCreated()
            .expectBody(SettlementRunResponse.class).returnResult().getResponseBody();

        client.get().uri(RUNS_PATH + "/{id}", created.runId())
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(SettlementRunResponse.class)
            .value(found -> assertThat(found.runId()).isEqualTo(created.runId()));
    }

    @Test
    @DisplayName("없는 실행 기록은 404이다")
    void getRunNotFound() {
        client.get().uri(RUNS_PATH + "/999")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec run(LocalDate cutoffOn) {
        return client.post().uri(RUN_PATH)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SettlementRunRequest(cutoffOn))
            .exchange();
    }
}
