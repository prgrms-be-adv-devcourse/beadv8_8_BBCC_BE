package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerApplicationResponse;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class SellerApplicationApiTest {

    private static final String PATH = "/api/v1/members/me/seller-application";
    private static final Long MEMBER_ID = 1L;
    private static final SellerApplicationRequest REQUEST = request(
        SellerFixture.BUSINESS_NAME, SellerFixture.BUSINESS_REGISTRATION_NUMBER);

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Test
    @DisplayName("판매자를 신청하면 201과 Location, PENDING 신청을 준다")
    void apply() {
        var result = submit(tokens.buyer(MEMBER_ID), REQUEST)
            .expectStatus().isCreated()
            .returnResult(SellerApplicationResponse.class);

        var application = result.getResponseBody();
        assertRawNumberNotExposed(result.getResponseBodyContent());
        assertThat(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(PATH);
        assertThat(application.status()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(application.representativeName()).isEqualTo(SellerFixture.REPRESENTATIVE_NAME);
        assertThat(application.maskedBusinessRegistrationNumber())
            .isEqualTo(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
    }

    @Test
    @DisplayName("신청 회원 닉네임은 토큰의 닉네임으로 저장한다")
    void applyStoresNickname() {
        apply(MEMBER_ID);

        var saved = sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(MEMBER_ID).orElseThrow();
        assertThat(saved.getApplicantNickname()).isEqualTo(TestTokens.nickname(MEMBER_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("잘못된 신청 내용은 400이다")
    void applyInvalid(String name, SellerApplicationRequest request) {
        submit(tokens.buyer(MEMBER_ID), request)
            .expectStatus().isBadRequest();
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
            Arguments.of("하이픈 있는 사업자등록번호",
                request(SellerFixture.BUSINESS_NAME, "214-86-12345")),
            Arguments.of("빈 상호명",
                request(" ", SellerFixture.BUSINESS_REGISTRATION_NUMBER)),
            Arguments.of("51자 상호명",
                request("가".repeat(51), SellerFixture.BUSINESS_REGISTRATION_NUMBER)),
            Arguments.of("9자리 사업자등록번호",
                request(SellerFixture.BUSINESS_NAME, "123456789")),
            Arguments.of("null 사업자등록번호",
                request(SellerFixture.BUSINESS_NAME, null)),
            Arguments.of("빈 대표자명",
                new SellerApplicationRequest(
                    SellerFixture.BUSINESS_NAME, " ", SellerFixture.BUSINESS_REGISTRATION_NUMBER)));
    }

    @Test
    @DisplayName("심사 중인 신청이 있는데 또 신청하면 409다")
    void applyTwice() {
        apply(MEMBER_ID);

        submit(tokens.buyer(MEMBER_ID), REQUEST)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.APPLICATION_ALREADY_PENDING.title());
    }

    @Test
    @DisplayName("이미 판매자면 신청할 수 없다. 409다")
    void applyWhenSeller() {
        sellerRepository.save(Seller.from(SellerFixture.approvedApplication(MEMBER_ID)));

        submit(tokens.buyer(MEMBER_ID), REQUEST)
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(SellerErrorCode.ALREADY_SELLER.title());
    }

    @Test
    @DisplayName("가장 최근 신청을 준다")
    void getLatest() {
        apply(MEMBER_ID);

        var result = client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isOk()
            .returnResult(SellerApplicationResponse.class);

        var application = result.getResponseBody();
        assertRawNumberNotExposed(result.getResponseBodyContent());
        assertThat(application.status()).isEqualTo(SellerApplicationStatus.PENDING);
        assertThat(application.businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(application.representativeName()).isEqualTo(SellerFixture.REPRESENTATIVE_NAME);
        assertThat(application.maskedBusinessRegistrationNumber())
            .isEqualTo(SellerFixture.MASKED_BUSINESS_REGISTRATION_NUMBER);
        assertThat(application.decidedAt()).isNull();
    }

    @Test
    @DisplayName("신청한 적이 없으면 조회는 404다")
    void getLatestWhenNone() {
        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(MEMBER_ID)))
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("토큰 없이 신청하면 401이다")
    void unauthenticated() {
        submit(null, REQUEST)
            .expectStatus().isUnauthorized();
    }

    // 응답 본문에 원본 사업자등록번호가 없어야 한다
    private static void assertRawNumberNotExposed(byte[] body) {
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain(SellerFixture.BUSINESS_REGISTRATION_NUMBER);
    }

    private static SellerApplicationRequest request(String businessName, String businessRegistrationNumber) {
        return new SellerApplicationRequest(
            businessName, SellerFixture.REPRESENTATIVE_NAME, businessRegistrationNumber);
    }

    private void apply(Long memberId) {
        submit(tokens.buyer(memberId), REQUEST)
            .expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec submit(String token, SellerApplicationRequest body) {
        return client.post().uri(PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }
}
