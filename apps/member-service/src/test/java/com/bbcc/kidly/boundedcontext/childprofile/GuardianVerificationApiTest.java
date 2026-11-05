package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.support.FakeIdentityVerificationClient;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class GuardianVerificationApiTest {
    private static final String PATH = "/api/v1/guardian-verification";
    @Autowired private RestTestClient client;

    @Test
    void verifyAndGet() {
        post(1L, "first").expectStatus().isCreated().expectHeader().valueEquals("Location", PATH)
            .expectBody().jsonPath("$.verified").isEqualTo(true);
        client.get().uri(PATH).headers(TestMembers.buyer(1L)).exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.verified").isEqualTo(true)
            .jsonPath("$.verifiedAt").isEqualTo("2026-11-05T10:49:00");
    }

    @Test
    void rejectsIncomplete() {
        post(1L, FakeIdentityVerificationClient.READY_ID).expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo("본인인증이 완료되지 않았습니다");
    }

    @Test
    void rejectsUnderage() {
        post(1L, FakeIdentityVerificationClient.UNDERAGE_ID).expectStatus().isBadRequest()
            .expectBody().jsonPath("$.title").isEqualTo("인증할 수 없는 나이입니다");
    }

    @Test
    void rejectsSameIdentityOnAnotherMember() {
        post(1L, "first").expectStatus().isCreated();
        post(2L, "second").expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("이미 인증된 사람입니다");
    }

    @Test
    void rejectsSecondVerificationForMember() {
        post(1L, "first").expectStatus().isCreated();
        post(1L, "second").expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.title").isEqualTo("이미 본인인증했습니다");
    }

    private RestTestClient.ResponseSpec post(Long memberId, String id) {
        return client.post().uri(PATH).headers(TestMembers.buyer(memberId)).contentType(MediaType.APPLICATION_JSON)
            .body("{\"identityVerificationId\":\"" + id + "\"}").exchange();
    }
}
