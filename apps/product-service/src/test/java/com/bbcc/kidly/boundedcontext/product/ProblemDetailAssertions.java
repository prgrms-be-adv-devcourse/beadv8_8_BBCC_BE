package com.bbcc.kidly.boundedcontext.product;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

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

    static void assertProblem(
        RestTestClient.ResponseSpec response, HttpStatus status, String title, String detail, String instance) {
        response.expectStatus().isEqualTo(status)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").exists()
            .jsonPath("$.title").isEqualTo(title)
            .jsonPath("$.status").isEqualTo(status.value())
            .jsonPath("$.detail").isEqualTo(detail)
            .jsonPath("$.instance").isEqualTo(instance);
    }
}
