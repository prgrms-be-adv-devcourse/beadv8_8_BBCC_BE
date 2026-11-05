package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 상품 서비스가 상품 도메인만으로 뜨고, 상품 API를 정해진 주소 · 응답 형식으로 내는지 본다(#791).
 */
@FunctionalTest
class ProductServiceApplicationApiTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    // baseline-version은 kidly-app이 마지막으로 적용한 상품 마이그레이션 버전이다. 실제 파일 버전이 아니면
    // kidly-app이 이미 적용한 DB에서 그 파일까지 건너뛰는 경계가 어긋난다
    @Value("${spring.flyway.baseline-version}")
    private String baselineVersion;

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
    @DisplayName("다른 도메인 API는 없어 ProblemDetail 형식의 404를 받는다")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/orders")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.title").isNotEmpty()
            .jsonPath("$.instance").isEqualTo("/api/v1/orders");
    }

    @Test
    @DisplayName("빈 DB에는 init부터 적용하고, baseline-version은 적용한 마이그레이션 버전 중 하나이며, 테이블은 상품 스키마에만 만든다")
    void migratesOnlyProductSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM product.flyway_schema_history_product
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("product");
        assertThat(versions.getFirst()).isEqualTo("202610160256");
        assertThat(versions).contains(baselineVersion);
    }

    @Test
    @DisplayName("카테고리 목록 API를 부르면 정해진 형식으로 답한다(로그인 없이, ID는 문자열)")
    void categories() {
        categoryRepository.save(CategoryFixture.clothing());

        client.get().uri("/api/v1/categories")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("의류")
            .jsonPath("$[0].categoryId").value(id -> assertThat(id).isInstanceOf(String.class))
            .jsonPath("$[0].leaf").isEqualTo(false)
            .jsonPath("$[0].children").isEmpty();
    }
}
