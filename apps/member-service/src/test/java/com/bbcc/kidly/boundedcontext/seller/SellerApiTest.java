package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.Map;
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

    @Autowired
    private CommittedEventRecorder eventRecorder;

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

    @Test
    @DisplayName("상호명을 바꾸면 응답과 조회에 반영되고 커밋된 이벤트가 한 번 나간다")
    void changeBusinessName() {
        sellerRepository.save(SellerFixture.activeSeller(MEMBER_ID));
        eventRecorder.clear();

        patch("새 상호").expectStatus().isOk().expectBody()
            .jsonPath("$.businessName").isEqualTo("새 상호");
        get(TestMembers.seller(MEMBER_ID)).expectStatus().isOk().expectBody()
            .jsonPath("$.businessName").isEqualTo("새 상호");
        assertThat(eventRecorder.events(SellerChangedEvent.class))
            .singleElement().satisfies(event -> {
                assertThat(event.data().sellerId()).isEqualTo(MEMBER_ID);
                assertThat(event.data().businessName()).isEqualTo("새 상호");
                assertThat(event.data().status()).isEqualTo(SellerStatus.ACTIVE);
            });
        eventRecorder.clear();
        patch("새 상호").expectStatus().isOk();
        assertThat(eventRecorder.events(SellerChangedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("빈 이름과 51자 이름은 거부한다")
    void invalidBusinessName() {
        sellerRepository.save(SellerFixture.activeSeller(MEMBER_ID));
        patch("").expectStatus().isBadRequest();
        patch("가".repeat(51)).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("없는 판매자의 상호명은 바꿀 수 없다")
    void changeMissing() {
        patch("새 상호").expectStatus().isNotFound();
    }

    @Test
    @DisplayName("정지된 판매자도 상호명을 바꾼다")
    void changeSuspended() {
        var seller = SellerFixture.activeSeller(MEMBER_ID);
        seller.suspend(SellerFixture.ADMIN_ID, "정지", LocalDateTime.now());
        sellerRepository.save(seller);

        patch("새 상호").expectStatus().isOk().expectBody()
            .jsonPath("$.businessName").isEqualTo("새 상호")
            .jsonPath("$.status").isEqualTo("SUSPENDED");
    }

    private RestTestClient.ResponseSpec patch(String businessName) {
        return client.patch().uri(PATH)
            .headers(TestMembers.seller(MEMBER_ID))
            .body(Map.of("businessName", businessName))
            .exchange();
    }

    private RestTestClient.ResponseSpec get(Consumer<HttpHeaders> login) {
        return client.get().uri(PATH)
            .headers(login)
            .exchange();
    }
}
