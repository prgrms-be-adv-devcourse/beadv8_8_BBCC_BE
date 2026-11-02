package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 추천 서비스가 추천 도메인만으로 뜨고, 추천 API 두 개를 kidly-app과 같은 주소 · 같은 응답 형식으로 내는지 본다.
 */
@FunctionalTest
class RecommendServiceApplicationApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @Test
    @DisplayName("health가 UP이다")
    void healthUp() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("다른 도메인 API는 없어 kidly-app과 같은 ProblemDetail 형식의 404를 받는다")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/children")
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isNotEmpty()
            .jsonPath("$.instance").isEqualTo("/api/v1/children");
    }

    // 추천 마이그레이션을 더하고 baseline-version을 올리지 않으면, kidly-app이 이미 적용한 DB에서 다시 적용하려다 기동에 실패한다
    @Value("${spring.flyway.baseline-version}")
    private String baselineVersion;

    @Test
    @DisplayName("빈 DB에는 init부터 적용하고, 마지막 버전은 baseline-version이며, 테이블은 추천 스키마에만 만든다")
    void migratesOnlyRecommendSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM recommend.flyway_schema_history_recommend
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("recommend");
        assertThat(versions.getFirst()).isEqualTo("202610160256");
        assertThat(versions.getLast()).isEqualTo(baselineVersion);
    }

    @Test
    @DisplayName("사이즈 추천 API를 kidly-app과 같은 주소로 부르면 같은 형식으로 답한다 (자녀 1, 상품 A → 120 · 여유 있음)")
    void recommendSize() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        client.get().uri("/api/v1/recommendations/size?productId={productId}&childId={childId}",
                PRODUCT_A_ID, CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.recommendedSize").isEqualTo("120")
            .jsonPath("$.fit").isEqualTo("ROOMY")
            .jsonPath("$.notices.length()").isEqualTo(0)
            .jsonPath("$.candidateSizes").isEqualTo(List.of("110", "120"))
            // ID는 문자열로, 없는 값은 칸을 빼지 않고 null로 보낸다
            .jsonPath("$").value(Map.class, body -> assertThat(body)
                .containsEntry("productId", "31")
                .containsEntry("childId", "11")
                .containsEntry("lastPurchasedSize", null));
    }

    @Test
    @DisplayName("맞춤 상품 목록 API를 kidly-app과 같은 주소로 부르면 같은 목록 형식으로 답한다")
    void listRecommendedProducts() {
        replicaProductService.applyProductChanged(RecommendFixture.productAChangedEvent());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());

        client.get().uri("/api/v1/recommendations/products?childId={childId}", CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(1)
            .jsonPath("$.content[0]").value(Map.class, card -> assertThat(card).isEqualTo(Map.of(
                "productId", "31",
                "name", "데일리 맨투맨",
                "thumbnailUrl", "https://cdn.kidly.test/products/31.jpg",
                "businessName", "소소한옷장",
                "minPrice", 25_000,
                "recommendedSize", "120")))
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(20)
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.totalPages").isEqualTo(1)
            .jsonPath("$.hasNext").isEqualTo(false);
    }
}
