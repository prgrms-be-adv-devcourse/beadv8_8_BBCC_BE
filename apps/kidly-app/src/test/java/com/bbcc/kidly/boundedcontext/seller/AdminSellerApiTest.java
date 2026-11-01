package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class AdminSellerApiTest {

    private static final String PATH = "/api/v1/admin/sellers";
    private static final String REASON = "허위 상품 등록으로 신고가 접수되었어요.";
    private static final Long ADMIN_ID = SellerFixture.ADMIN_ID;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerRepository sellerRepository;

    @Test
    @DisplayName("정지하면 SUSPENDED 판매자를 돌려준다")
    void suspend() {
        var seller = sellerRepository.save(SellerFixture.activeSeller(1L));

        var body = suspend(seller.getId(), REASON).expectStatus().isOk()
            .returnResult(SellerResponse.class).getResponseBody();

        assertThat(body.sellerId()).isEqualTo(seller.getId());
        assertThat(body.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(body.status()).isEqualTo(SellerStatus.SUSPENDED);
        var saved = sellerRepository.findById(seller.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(saved.getSuspension().reason()).isEqualTo(REASON);
        assertThat(saved.getSuspension().suspendedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("이미 정지된 판매자를 또 정지하면 409다")
    void suspendTwice() {
        var seller = sellerRepository.save(SellerFixture.activeSeller(1L));
        suspend(seller.getId(), REASON).expectStatus().isOk();

        suspend(seller.getId(), REASON).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.SELLER_ALREADY_SUSPENDED.title());
    }

    @Test
    @DisplayName("없는 판매자를 정지하면 404다")
    void suspendMissing() {
        suspend(999L, REASON).expectStatus().isNotFound();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("정지 사유가 비어 있으면 400이다")
    void suspendBlankReason(String reason) {
        var seller = sellerRepository.save(SellerFixture.activeSeller(1L));

        suspend(seller.getId(), reason).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("정지 사유가 200자를 넘으면 400이다")
    void suspendLongReason() {
        var seller = sellerRepository.save(SellerFixture.activeSeller(1L));

        suspend(seller.getId(), "가".repeat(SellerPolicy.SUSPEND_REASON_MAX_LENGTH + 1))
            .expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec suspend(Long sellerId, String reason) {
        return client.post().uri(PATH + "/{id}/suspend", sellerId)
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new SellerSuspendRequest(reason))
            .exchange();
    }
}
