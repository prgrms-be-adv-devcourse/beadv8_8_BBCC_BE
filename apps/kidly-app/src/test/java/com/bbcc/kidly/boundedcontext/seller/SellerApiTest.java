package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerApiTest {

    private static final String PATH = "/api/v1/seller/me";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private SellerRepository sellerRepository;

    @Test
    @DisplayName("판매자 정보를 준다")
    void getMine() {
        sellerRepository.save(Seller.from(SellerFixture.approvedApplication(MEMBER_ID)));

        var seller = get(tokens.seller(MEMBER_ID))
            .expectStatus().isOk()
            .returnResult(SellerResponse.class)
            .getResponseBody();

        assertThat(seller.sellerId()).isEqualTo(MEMBER_ID);
        assertThat(seller.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(seller.status()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("판매자가 아니면 403이다")
    void forbiddenForBuyer() {
        get(tokens.buyer(MEMBER_ID))
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("토큰 없이 조회하면 401이다")
    void unauthenticated() {
        get(null)
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("판매자 정보가 없으면 404다")
    void getMineWhenNone() {
        get(tokens.seller(MEMBER_ID))
            .expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec get(String token) {
        return client.get().uri(PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .exchange();
    }
}
