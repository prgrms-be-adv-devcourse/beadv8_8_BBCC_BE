package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerApiTest {

    private static final String PATH = "/api/v1/seller/me";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerRepository sellerRepository;

    @Test
    @DisplayName("판매자 정보를 준다")
    void getMine() {
        sellerRepository.save(Seller.from(SellerFixture.approvedApplication(MEMBER_ID)));

        var seller = get(TestMembers.seller(MEMBER_ID))
            .expectStatus().isOk()
            .returnResult(SellerResponse.class)
            .getResponseBody();

        assertThat(seller.sellerId()).isEqualTo(MEMBER_ID);
        assertThat(seller.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(seller.status()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("토큰 없이 조회하면 401이다")
    void unauthenticated() {
        get(TestMembers.anonymous())
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("판매자 정보가 없으면 404다")
    void getMineWhenNone() {
        get(TestMembers.seller(MEMBER_ID))
            .expectStatus().isNotFound();
    }

    private RestTestClient.ResponseSpec get(Consumer<HttpHeaders> login) {
        return client.get().uri(PATH)
            .headers(login)
            .exchange();
    }
}
