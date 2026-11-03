package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildProfileResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀 프로필 서비스가 자녀 프로필 도메인만으로 뜨고, 빈 DB에 자녀 스키마만 만드는지 본다.
 */
@FunctionalTest
class ChildProfileServiceApplicationApiTest {

    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("다른 도메인 API는 없다(404)")
    void noOtherDomainApi() {
        client.get().uri("/api/v1/members/me")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("자녀를 등록하고 목록으로 조회한다")
    void registerAndList() {
        var request = new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE);
        var registered = client.post().uri("/api/v1/children")
            .headers(TestMembers.buyer(MEMBER_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .exchange()
            .expectStatus().isCreated()
            .returnResult(ChildProfileResponse.class)
            .getResponseBody();

        var children = client.get().uri("/api/v1/children")
            .headers(TestMembers.buyer(MEMBER_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<List<ChildProfileResponse>>() { })
            .returnResult()
            .getResponseBody();

        assertThat(children).extracting(ChildProfileResponse::id).containsExactly(registered.id());
    }

    @Test
    @DisplayName("빈 DB에는 init부터 적용하고, 테이블은 자녀 스키마에만 만든다")
    void migratesOnlyChildProfileSchema() {
        var schemas = jdbcTemplate.queryForList("""
            SELECT DISTINCT schemaname FROM pg_tables
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """, String.class);
        // version이 없는 행은 Flyway가 스키마를 만든 기록이다
        var versions = jdbcTemplate.queryForList("""
            SELECT version FROM childprofile.flyway_schema_history_childprofile
            WHERE success AND version IS NOT NULL ORDER BY installed_rank
            """, String.class);

        assertThat(schemas).containsExactly("childprofile");
        assertThat(versions).containsExactly("202610160256", "202610300953", "202611021428");
    }
}
