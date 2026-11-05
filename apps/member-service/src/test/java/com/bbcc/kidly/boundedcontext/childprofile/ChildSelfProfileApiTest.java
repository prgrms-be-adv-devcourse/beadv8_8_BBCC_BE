package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.ChildAccountLinkService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.member.support.security.TestChildren;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class ChildSelfProfileApiTest {

    private static final long GUARDIAN_ID = 11L;
    private static final long ACCOUNT_ID = 21L;

    @Autowired private RestTestClient client;
    @Autowired private ChildProfileService service;
    @Autowired private ChildAccountLinkService linkService;

    @Test
    void returnsLinkedProfileSameAsGuardianDetail() {
        var childId = registerChild("첫째");
        link(ACCOUNT_ID, childId);

        var guardianBody = client.get().uri("/api/v1/children/{id}", childId).headers(TestMembers.buyer(GUARDIAN_ID))
            .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        var ownBody = getOwnProfile(ACCOUNT_ID, GUARDIAN_ID)
            .expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();

        assertThat(ownBody).isEqualTo(guardianBody);
    }

    @Test
    void returnsOnlyOwnProfileWhenSiblingIsLinked() {
        var childId = registerChild("첫째");
        var siblingId = registerChild("둘째");
        link(ACCOUNT_ID, childId);
        link(24L, siblingId);

        getOwnProfile(ACCOUNT_ID, GUARDIAN_ID).expectStatus().isOk()
            .expectBody().jsonPath("$.id").isEqualTo(childId.intValue());
    }

    @Test
    void notFoundWithoutLink() {
        link(ACCOUNT_ID, registerChild("첫째"));

        getOwnProfile(22L, GUARDIAN_ID).expectStatus().isNotFound();
    }

    @Test
    void notFoundWhenGuardianDiffers() {
        link(ACCOUNT_ID, registerChild("첫째"));

        getOwnProfile(ACCOUNT_ID, 12L).expectStatus().isNotFound();
    }

    @Test
    void notFoundAfterAccountDeleted() {
        var childId = registerChild("첫째");
        link(ACCOUNT_ID, childId);
        linkService.applyAccountChange(ACCOUNT_ID, childId, true, LocalDateTime.now());

        getOwnProfile(ACCOUNT_ID, GUARDIAN_ID).expectStatus().isNotFound();
    }

    @Test
    void notFoundAfterProfileDeleted() {
        var childId = registerChild("첫째");
        link(ACCOUNT_ID, childId);
        service.delete(GUARDIAN_ID, childId);

        getOwnProfile(ACCOUNT_ID, GUARDIAN_ID).expectStatus().isNotFound();
    }

    @Test
    void unauthorizedWithMemberToken() {
        link(ACCOUNT_ID, registerChild("첫째"));

        client.get().uri("/api/v1/child/profile").headers(TestMembers.buyer(GUARDIAN_ID)).exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void childTokenCannotUseGuardianChildrenApi() {
        var childId = registerChild("첫째");
        link(ACCOUNT_ID, childId);

        client.get().uri("/api/v1/children").headers(TestChildren.of(ACCOUNT_ID, GUARDIAN_ID)).exchange()
            .expectStatus().isUnauthorized();
        client.get().uri("/api/v1/children/{id}", childId).headers(TestChildren.of(ACCOUNT_ID, GUARDIAN_ID))
            .exchange().expectStatus().isUnauthorized();
    }

    private Long registerChild(String name) {
        return service.register(GUARDIAN_ID, new ChildProfileRequest(name, LocalDate.of(2019, 1, 15),
            Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"), null, null, true, true)).id();
    }

    private void link(Long accountId, Long childId) {
        linkService.applyAccountChange(accountId, childId, false, LocalDateTime.now());
    }

    private RestTestClient.ResponseSpec getOwnProfile(Long accountId, Long guardianId) {
        return client.get().uri("/api/v1/child/profile").headers(TestChildren.of(accountId, guardianId)).exchange();
    }
}
