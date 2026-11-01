package com.bbcc.kidly.global.config;

import com.bbcc.kidly.support.FunctionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SwaggerApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("API 문서에 @LoginMember AuthMember를 요청 파라미터로 넣지 않는다")
    void loginMemberIsNotRequestParameter() {
        client.get().uri("/v3/api-docs")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.paths['/api/v1/members/me'].get.parameters").doesNotExist()
            .jsonPath("$.paths['/api/v1/admin/members/{memberId}/revoke-admin'].post.parameters[*].name")
            .isEqualTo(List.of("memberId"))
            .jsonPath("$.paths..parameters[?(@.name == 'member')]").isEmpty();
    }
}
