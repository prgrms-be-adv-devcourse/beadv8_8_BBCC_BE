package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 더보기 목록이 1쪽 기준 시각(asOf)과 저장된 선호로 순서를 정하는 동작을 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class RecommendedProductRankingApiTest {

    private static final String PATH = "/api/v1/recommendations/products";

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @BeforeEach
    void setUp() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    @Test
    @DisplayName("PREF-09 asOf를 주면 응답의 asOf로 그 값을 그대로 돌려준다")
    void echoGivenAsOf() {
        list("&asOf=2026-11-09T10:00:00")
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.asOf").isEqualTo("2026-11-09T10:00:00");
    }

    @Test
    @DisplayName("PREF-09 asOf 없이 요청하면 지금 시각(한국 시간, 초 단위)을 응답의 asOf로 돌려준다")
    void useNowWhenAsOfIsMissing() {
        LocalDateTime before = LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS);

        String asOf = list("")
            .expectStatus().isOk()
            .returnResult(AsOfOnly.class)
            .getResponseBody()
            .asOf();

        LocalDateTime after = LocalDateTime.now(SERVICE_ZONE);
        assertThat(LocalDateTime.parse(asOf)).isBetween(before, after).hasNano(0);
    }

    private RestTestClient.ResponseSpec list(String query) {
        return client.get().uri(PATH + "?childId={childId}" + query, CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange();
    }

    private record AsOfOnly(String asOf) {
    }
}
