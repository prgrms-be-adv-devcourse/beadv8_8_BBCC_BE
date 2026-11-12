package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class DevGuardianVerificationApiTest {

    private static final String PATH = "/api/v1/dev/guardian-verifications";

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("본인인증 기록을 만들면 자녀를 등록할 수 있다")
    void unlocksChildRegistration() {
        post(1L).expectStatus().isOk().expectBody()
            .jsonPath("$.memberId").isEqualTo(1).jsonPath("$.created").isEqualTo(true);

        var child = new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 1), Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), null, null, true, false);
        client.post().uri("/api/v1/children").headers(TestMembers.buyer(1L)).body(child).exchange()
            .expectStatus().isCreated();
    }

    @Test
    @DisplayName("이미 기록이 있으면 그대로 두고 created가 false다")
    void idempotent() {
        post(1L).expectStatus().isOk();

        post(1L).expectStatus().isOk().expectBody()
            .jsonPath("$.memberId").isEqualTo(1).jsonPath("$.created").isEqualTo(false);
        client.get().uri("/api/v1/guardian-verification").headers(TestMembers.buyer(1L)).exchange()
            .expectBody().jsonPath("$.verified").isEqualTo(true);
    }

    @Test
    @DisplayName("회원마다 따로 만들어지고 로그인하지 않으면 401이다")
    void perMemberAndRequiresLogin() {
        post(1L).expectStatus().isOk();
        post(2L).expectStatus().isOk().expectBody().jsonPath("$.created").isEqualTo(true);

        client.post().uri(PATH).headers(TestMembers.anonymous()).exchange().expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec post(Long memberId) {
        return client.post().uri(PATH).headers(TestMembers.buyer(memberId)).exchange();
    }
}
