package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.domain.MemberSuspension;
import com.bbcc.kidly.boundedcontext.member.in.dto.AdminMemberSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
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
class AdminMemberApiTest {

    private static final String PATH = "/api/v1/admin/members";
    private static final String REASON = "욕설이 담긴 리뷰로 신고가 접수되었어요.";
    private static final Long ADMIN_ID = 100L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("목록은 최신 가입순이고 정지 사유 · 판매자 상태를 준다")
    void list() {
        var buyer = memberRepository.save(MemberFixture.kakaoMember("1"));
        var seller = memberRepository.save(MemberFixture.withSellerState("2", SellerStatus.SUSPENDED));
        suspend(buyer.getId(), REASON).expectStatus().isNoContent();

        var page = list("");

        assertThat(page.content()).extracting(AdminMemberSummaryResponse::id)
            .containsExactly(seller.getId(), buyer.getId());
        var sellerRow = page.content().getFirst();
        assertThat(sellerRow.nickname()).isEqualTo(seller.getNickname());
        assertThat(sellerRow.status()).isEqualTo(MemberStatus.ACTIVE.name());
        assertThat(sellerRow.sellerStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(sellerRow.createdAt()).isNotNull();
        var buyerRow = page.content().getLast();
        assertThat(buyerRow.status()).isEqualTo(MemberStatus.SUSPENDED.name());
        assertThat(buyerRow.sellerStatus()).isNull();
        assertThat(buyerRow.suspendReason()).isEqualTo(REASON);
        assertThat(buyerRow.suspendedAt()).isNotNull();
    }

    @Test
    @DisplayName("filter로 거르면 그 조건의 회원만 준다")
    void listByFilter() {
        var buyer = memberRepository.save(MemberFixture.kakaoMember("1"));
        memberRepository.save(MemberFixture.kakaoMember("2"));
        suspend(buyer.getId(), REASON).expectStatus().isNoContent();

        var page = list("?filter=SUSPENDED");

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(buyer.getId());
    }

    @Test
    @DisplayName("모르는 filter는 400이다")
    void listInvalidFilter() {
        client.get().uri(PATH + "?filter=FOO")
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("구매자는 목록을 볼 수 없다. 403이다")
    void listForbiddenForBuyer() {
        client.get().uri(PATH)
            .headers(headers -> headers.setBearerAuth(tokens.buyer(2L)))
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void listUnauthorized() {
        client.get().uri(PATH).exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("정지하면 204이고 회원은 SUSPENDED가 된다")
    void suspend() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        suspend(member.getId(), REASON).expectStatus().isNoContent();

        var saved = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(saved.getSuspension().reason()).isEqualTo(REASON);
        assertThat(saved.getSuspension().suspendedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("정지된 회원은 정지 전에 받은 Refresh Token으로 재발급할 수 없다. 401이다")
    void reissueAfterSuspend() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var refreshToken = refreshTokenService.issue(member.getId());

        suspend(member.getId(), REASON).expectStatus().isNoContent();

        client.post().uri("/api/v1/auth/reissue")
            .cookie("refresh_token", refreshToken)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("이미 정지된 회원을 또 정지하면 409다")
    void suspendTwice() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        suspend(member.getId(), REASON).expectStatus().isNoContent();

        suspend(member.getId(), REASON).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(MemberErrorCode.MEMBER_NOT_SUSPENDABLE.title());
    }

    @Test
    @DisplayName("관리자 회원은 정지할 수 없다. 409다")
    void suspendAdmin() {
        var admin = memberRepository.save(MemberFixture.withRole(MemberRole.ADMIN));

        suspend(admin.getId(), REASON).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(MemberErrorCode.MEMBER_NOT_SUSPENDABLE.title());
    }

    @Test
    @DisplayName("없는 회원을 정지하면 404다")
    void suspendMissing() {
        suspend(999L, REASON).expectStatus().isNotFound();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("정지 사유가 비어 있으면 400이다")
    void suspendBlankReason(String reason) {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        suspend(member.getId(), reason).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("정지 사유가 200자를 넘으면 400이다")
    void suspendLongReason() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        suspend(member.getId(), "가".repeat(MemberSuspension.REASON_MAX_LENGTH + 1))
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("구매자는 정지할 수 없다. 403이다")
    void suspendForbiddenForBuyer() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        client.post().uri(PATH + "/{id}/suspend", member.getId())
            .headers(headers -> headers.setBearerAuth(tokens.buyer(2L)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new MemberSuspendRequest(REASON))
            .exchange()
            .expectStatus().isForbidden();
    }

    private PageResponse<AdminMemberSummaryResponse> list(String query) {
        return client.get().uri(PATH + query)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<PageResponse<AdminMemberSummaryResponse>>() { })
            .returnResult()
            .getResponseBody();
    }

    private RestTestClient.ResponseSpec suspend(Long memberId, String reason) {
        return client.post().uri(PATH + "/{id}/suspend", memberId)
            .headers(headers -> headers.setBearerAuth(tokens.admin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new MemberSuspendRequest(reason))
            .exchange();
    }
}
