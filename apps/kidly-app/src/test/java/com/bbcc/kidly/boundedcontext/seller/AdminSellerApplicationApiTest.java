package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationSummaryResponse;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminSellerApplicationApiTest {

    private static final String PATH = "/api/v1/admin/seller-applications";
    private static final String APPLY_PATH = "/api/v1/members/me/seller-application";
    private static final Long ADMIN_ID = SellerFixture.ADMIN_ID;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Test
    @DisplayName("신청 목록을 최신순으로 페이지 응답에 담아 준다. 사업자등록번호는 가린다")
    void list() {
        apply(1L);
        apply(2L);

        var page = list("");

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).extracting(SellerApplicationSummaryResponse::applicantId)
            .containsExactly(2L, 1L);
        var row = page.content().getFirst();
        assertThat(row.applicantNickname()).isEqualTo(TestMembers.nickname(2L));
        assertThat(row.maskedBusinessRegistrationNumber()).isEqualTo(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
        assertThat(row.status()).isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("status로 거르면 그 상태의 신청만 준다")
    void listByStatus() {
        var approvedId = apply(1L);
        apply(2L);
        approve(approvedId).expectStatus().isOk();

        var page = list("?status=PENDING");

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().status()).isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("모르는 status는 400이다")
    void listInvalidStatus() {
        client.get().uri(PATH + "?status=FOO")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("승인하면 APPROVED 신청을 돌려주고, 신청자의 신청 조회도 APPROVED다")
    void approve() {
        var applicationId = apply(1L);

        var body = approve(applicationId).expectStatus().isOk()
            .returnResult(SellerApplicationSummaryResponse.class).getResponseBody();

        assertThat(body.id()).isEqualTo(applicationId);
        assertThat(body.status()).isEqualTo(SellerApplicationStatus.APPROVED);
        var mine = client.get().uri(APPLY_PATH)
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isOk()
            .returnResult(SellerApplicationResponse.class)
            .getResponseBody();
        assertThat(mine.status()).isEqualTo(SellerApplicationStatus.APPROVED);
        assertThat(mine.rejectReason()).isNull();
    }

    @Test
    @DisplayName("이미 승인한 신청을 또 승인하면 409다")
    void approveTwice() {
        var applicationId = apply(1L);
        approve(applicationId).expectStatus().isOk();

        approve(applicationId).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING.title());
    }

    @Test
    @DisplayName("없는 신청을 승인하면 404다")
    void approveMissing() {
        approve(999L).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("같은 회원의 심사 중 신청이 둘이면 첫 승인만 성공하고 둘째는 ALREADY_SELLER 409다")
    void approveSecondPendingOfSameMember() {
        var first = sellerApplicationRepository.save(SellerFixture.application(1L)).getId();
        var second = sellerApplicationRepository.save(SellerFixture.application(1L)).getId();

        approve(first).expectStatus().isOk();

        approve(second).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.ALREADY_SELLER.title());
    }

    @Test
    @DisplayName("구매자는 목록을 볼 수 없다. 403이다")
    void forbiddenForBuyer() {
        client.get().uri(PATH)
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("구매자는 승인할 수 없다. 403이다")
    void approveForbiddenForBuyer() {
        var applicationId = apply(1L);

        client.post().uri(PATH + "/{id}/approve", applicationId)
            .headers(TestMembers.buyer(2L))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("거절하면 REJECTED 신청을 돌려주고, 신청자의 신청 조회에 거절 사유가 보인다")
    void reject() {
        var applicationId = apply(1L);

        var body = reject(applicationId, SellerFixture.REJECT_REASON).expectStatus().isOk()
            .returnResult(SellerApplicationSummaryResponse.class).getResponseBody();

        assertThat(body.id()).isEqualTo(applicationId);
        assertThat(body.status()).isEqualTo(SellerApplicationStatus.REJECTED);
        var mine = client.get().uri(APPLY_PATH)
            .headers(TestMembers.buyer(1L))
            .exchange()
            .expectStatus().isOk()
            .returnResult(SellerApplicationResponse.class)
            .getResponseBody();
        assertThat(mine.status()).isEqualTo(SellerApplicationStatus.REJECTED);
        assertThat(mine.rejectReason()).isEqualTo(SellerFixture.REJECT_REASON);
        assertThat(mine.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("거절된 신청은 status=REJECTED로 거를 수 있다")
    void listRejected() {
        var rejectedId = apply(1L);
        apply(2L);
        reject(rejectedId, SellerFixture.REJECT_REASON).expectStatus().isOk();

        var page = list("?status=REJECTED");

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(rejectedId);
    }

    @Test
    @DisplayName("이미 승인한 신청을 거절하면 409다")
    void rejectApproved() {
        var applicationId = apply(1L);
        approve(applicationId).expectStatus().isOk();

        reject(applicationId, SellerFixture.REJECT_REASON).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.APPLICATION_NOT_PENDING.title());
    }

    @Test
    @DisplayName("없는 신청을 거절하면 404다")
    void rejectMissing() {
        reject(999L, SellerFixture.REJECT_REASON).expectStatus().isNotFound();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("거절 사유가 비어 있으면 400이다")
    void rejectBlankReason(String reason) {
        var applicationId = apply(1L);

        reject(applicationId, reason).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("거절 사유가 200자를 넘으면 400이다")
    void rejectLongReason() {
        var applicationId = apply(1L);

        reject(applicationId, "가".repeat(SellerPolicy.REJECT_REASON_MAX_LENGTH + 1))
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("구매자는 거절할 수 없다. 403이다")
    void rejectForbiddenForBuyer() {
        var applicationId = apply(1L);

        client.post().uri(PATH + "/{id}/reject", applicationId)
            .headers(TestMembers.buyer(2L))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SellerApplicationRejectRequest(SellerFixture.REJECT_REASON))
            .exchange()
            .expectStatus().isForbidden();
    }

    private PageResponse<SellerApplicationSummaryResponse> list(String query) {
        return client.get().uri(PATH + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<PageResponse<SellerApplicationSummaryResponse>>() { })
            .returnResult()
            .getResponseBody();
    }

    private RestTestClient.ResponseSpec approve(Long applicationId) {
        return client.post().uri(PATH + "/{id}/approve", applicationId)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec reject(Long applicationId, String reason) {
        return client.post().uri(PATH + "/{id}/reject", applicationId)
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SellerApplicationRejectRequest(reason))
            .exchange();
    }

    // 실제 신청 API로 신청을 만들고 신청 ID를 준다
    private Long apply(Long memberId) {
        client.post().uri(APPLY_PATH)
            .headers(TestMembers.buyer(memberId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SellerApplicationRequest(SellerFixture.BUSINESS_NAME, SellerFixture.REPRESENTATIVE_NAME,
                SellerFixture.BUSINESS_REGISTRATION_NUMBER))
            .exchange()
            .expectStatus().isCreated();
        return sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(memberId).orElseThrow().getId();
    }
}
