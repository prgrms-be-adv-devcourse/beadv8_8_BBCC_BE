package com.bbcc.kidly.boundedcontext.product;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 기능 테스트에서 실패 응답이 ProblemDetail 5필드를 담은 application/problem+json인지 확인한다.
 */
final class ProblemDetailAssertions {

    private ProblemDetailAssertions() {
    }

    static void assertProblem(RestTestClient.ResponseSpec response, HttpStatus status) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").exists()
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").exists()
            .jsonPath("$.instance").exists();
    }
}
