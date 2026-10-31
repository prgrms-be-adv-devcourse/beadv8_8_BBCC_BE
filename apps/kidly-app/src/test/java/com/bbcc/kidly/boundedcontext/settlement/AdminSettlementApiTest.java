package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_END;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.PERIOD_START;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementJobConfig;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunRequest;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementRunResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminSettlementApiTest {

    private static final String RUN_PATH = "/api/v1/admin/settlements/run";
    private static final String RUNS_PATH = "/api/v1/admin/settlement-runs";
    private static final String SETTLEMENTS_PATH = "/api/v1/admin/settlements";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("관리자가 기준일로 정산을 실행하면 201과 Location, 실행 기록을 준다")
    void run() {
        var cutoffOn = LocalDate.now().withDayOfMonth(1);

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
        assertThat(body.skippedSellers()).isEmpty();
        assertThat(result.getResponseHeaders().getLocation().getPath()).isEqualTo(RUNS_PATH + "/" + body.runId());
    }

    @Test
    @DisplayName("기준일이 없으면 400이다")
    void runMissingCutoff() {
        client.post().uri(RUN_PATH)
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .exchange()
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("미래 기준일이면 400이다")
    void runFutureCutoff() {
        run(LocalDate.now().plusDays(1)).expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo("정산 기준일이 올바르지 않습니다");
    }

    @Test
    @DisplayName("이미 정산한 기준일이면 409이다")
    void runAlreadyRun() {
        var cutoffOn = LocalDate.now().withDayOfMonth(1);
        run(cutoffOn).expectStatus().isCreated();

        run(cutoffOn).expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("판매자는 실행할 수 없다. 403이다")
    void runForbidden() {
        client.post().uri(RUN_PATH)
            .headers(TestMembers.seller(2L))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SettlementRunRequest(LocalDate.now().withDayOfMonth(1)))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("실행 기록을 id로 조회한다")
    void getRun() {
        var created = run(LocalDate.now().withDayOfMonth(1)).expectStatus().isCreated()
            .expectBody(SettlementRunResponse.class).returnResult().getResponseBody();

        client.get().uri(RUNS_PATH + "/{id}", created.runId())
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.runId").isEqualTo(created.runId())
            .jsonPath("$.skippedSellers").isArray()
            .jsonPath("$.skippedSellers").isEmpty();
    }

    @Test
    @DisplayName("실행 중으로 남은 실행을 정리하면 200과 FAILED 실행 기록을 준다")
    void recoverRun() {
        var parameters = new JobParametersBuilder()
            .addLocalDateTime(SettlementJobConfig.CUTOFF_AT, LocalDate.now().withDayOfMonth(1).atStartOfDay())
            .toJobParameters();
        var instance = jobRepository.createJobInstance(SettlementJobConfig.JOB_NAME, parameters);
        var stuck = jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
        stuck.setStatus(BatchStatus.STARTED);
        stuck.setStartTime(LocalDateTime.now());
        jobRepository.update(stuck);

        recover(stuck.getId()).expectStatus().isOk()
            .expectBody(SettlementRunResponse.class)
            .value(run -> {
                assertThat(run.status()).isEqualTo("FAILED");
                assertThat(run.finishedAt()).isNotNull();
            });
    }

    @Test
    @DisplayName("끝난 실행을 정리하면 409, 없는 실행이면 404이다")
    void recoverRunRejected() {
        var completed = run(LocalDate.now().withDayOfMonth(1)).expectStatus().isCreated()
            .expectBody(SettlementRunResponse.class).returnResult().getResponseBody();

        recover(completed.runId()).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("정리할 수 없는 정산 실행입니다");
        recover(999L).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("없는 실행 기록은 404이다")
    void getRunNotFound() {
        client.get().uri(RUNS_PATH + "/999")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("관리자는 모든 판매자의 정산서를 보고, 판매자로 거를 수 있다")
    void settlements() {
        settlementRepository.save(SettlementFixture.settlement(10L, PERIOD_START, PERIOD_END));
        settlementRepository.save(SettlementFixture.settlement(11L, PERIOD_START, PERIOD_END));

        getSettlements(SETTLEMENTS_PATH).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(2);
        getSettlements(SETTLEMENTS_PATH + "?sellerId=11").expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.content[0].sellerId").isEqualTo(11)
            .jsonPath("$.content[0].status").isEqualTo("COMPLETED")
            .jsonPath("$.content[0].failReason").isEmpty();
    }

    @Test
    @DisplayName("정산서가 없으면 빈 목록이다")
    void settlementsEmpty() {
        getSettlements(SETTLEMENTS_PATH).expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.content").isEmpty();
    }

    @Test
    @DisplayName("상태 값이 잘못되면 400이다")
    void settlementsInvalidStatus() {
        getSettlements(SETTLEMENTS_PATH + "?status=READY").expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("판매자는 관리자 정산서 목록을 볼 수 없다. 403이다")
    void settlementsForbidden() {
        client.get().uri(SETTLEMENTS_PATH)
            .headers(TestMembers.seller(2L))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("지급 결과를 받지 못한 정산서를 재요청하면 204이고, 지급 완료된 뒤 다시 요청하면 409이다")
    void retryPayout() {
        var settlement = settlementRepository.save(SettlementFixture.settlement(10_000L));

        retry(settlement.getId()).expectStatus().isNoContent();

        assertThat(settlementRepository.findById(settlement.getId()).orElseThrow().getStatus())
            .isEqualTo(SettlementStatus.PAID);
        retry(settlement.getId()).expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("지급을 다시 요청할 수 없는 정산서입니다");
    }

    @Test
    @DisplayName("없는 정산서를 재요청하면 404이다")
    void retryPayoutNotFound() {
        retry(999L).expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec getSettlements(String uri) {
        return client.get().uri(uri)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec recover(Long runId) {
        return client.post().uri(RUNS_PATH + "/{id}/recover", runId)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec retry(Long settlementId) {
        return client.post().uri(SETTLEMENTS_PATH + "/{id}/retry", settlementId)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec run(LocalDate cutoffOn) {
        return client.post().uri(RUN_PATH)
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SettlementRunRequest(cutoffOn))
            .exchange();
    }
}
