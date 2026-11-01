package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.RefreshTokenService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberPolicy;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.in.dto.AdminMemberSummaryResponse;
import com.bbcc.kidly.boundedcontext.member.in.dto.LoginResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.IssuedAccessToken;
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
        assertThat(sellerRow.email()).isEqualTo(seller.getEmail());
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
    @DisplayName("keyword로 회원 번호를 검색한다")
    void listByKeyword() {
        memberRepository.save(MemberFixture.kakaoMember("1"));
        var second = memberRepository.save(MemberFixture.kakaoMember("2"));

        var page = list("?filter=ALL&keyword=" + second.getId());

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("keyword로 이메일을 검색한다")
    void listByEmailKeyword() {
        memberRepository.save(MemberFixture.kakaoMember("1"));
        var second = memberRepository.save(MemberFixture.kakaoMember("2"));

        var page = list("?keyword=" + second.getEmail());

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(second.getId());
    }

    @Test
    @DisplayName("모르는 filter는 400이다")
    void listInvalidFilter() {
        client.get().uri(PATH + "?filter=FOO")
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isBadRequest();
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

        suspend(member.getId(), "가".repeat(MemberPolicy.SUSPEND_REASON_MAX_LENGTH + 1))
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("filter=ADMIN이면 관리자만 준다")
    void listAdmins() {
        memberRepository.save(MemberFixture.kakaoMember("1"));
        var admin = memberRepository.save(MemberFixture.withRole("2", MemberRole.ADMIN));

        var page = list("?filter=ADMIN");

        assertThat(page.content()).extracting(AdminMemberSummaryResponse::id).containsExactly(admin.getId());
    }

    @Test
    @DisplayName("관리자로 지정하면 204이고 ADMIN 필터에 나온다")
    void grantAdmin() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        grantAdmin(member.getId()).expectStatus().isNoContent();

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(list("?filter=ADMIN").content()).extracting(AdminMemberSummaryResponse::id)
            .containsExactly(member.getId());
    }

    @Test
    @DisplayName("지정된 회원은 재발급받은 토큰으로 관리자 API를 호출할 수 있다")
    void grantedMemberGetsAdminTokenOnReissue() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var refreshToken = refreshTokenService.issue(member.getId());

        grantAdmin(member.getId()).expectStatus().isNoContent();

        var accessToken = client.post().uri("/api/v1/auth/reissue")
            .cookie("refresh_token", refreshToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody(LoginResponse.class)
            .returnResult()
            .getResponseBody()
            .accessToken();
        var reissued = IssuedAccessToken.decode(accessToken);
        assertThat(reissued.roles()).contains(Role.ADMIN);
        client.get().uri(PATH)
            .headers(reissued.headers())
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("정지된 회원은 관리자로 지정할 수 없다. 409다")
    void grantAdminToSuspended() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        suspend(member.getId(), REASON).expectStatus().isNoContent();

        grantAdmin(member.getId()).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(MemberErrorCode.ADMIN_NOT_GRANTABLE.title());
    }

    @Test
    @DisplayName("없는 회원을 관리자로 지정하면 404다")
    void grantAdminMissing() {
        grantAdmin(999L).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("관리자 권한을 해제하면 204이고 USER가 된다")
    void revokeAdmin() {
        var admin = memberRepository.save(MemberFixture.withRole(MemberRole.ADMIN));

        revokeAdmin(admin.getId(), ADMIN_ID).expectStatus().isNoContent();

        assertThat(memberRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("본인의 관리자 권한은 해제할 수 없다. 409다")
    void revokeOwnAdmin() {
        var admin = memberRepository.save(MemberFixture.withRole(MemberRole.ADMIN));

        revokeAdmin(admin.getId(), admin.getId()).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.title").isEqualTo(MemberErrorCode.ADMIN_NOT_REVOCABLE.title());
    }

    @Test
    @DisplayName("관리자가 아닌 회원은 해제할 수 없다. 409다")
    void revokeAdminFromUser() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        revokeAdmin(member.getId(), ADMIN_ID).expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("없는 회원의 관리자 권한을 해제하면 404다")
    void revokeAdminMissing() {
        revokeAdmin(999L, ADMIN_ID).expectStatus().isNotFound();
    }

    private PageResponse<AdminMemberSummaryResponse> list(String query) {
        return client.get().uri(PATH + query)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody(new ParameterizedTypeReference<PageResponse<AdminMemberSummaryResponse>>() { })
            .returnResult()
            .getResponseBody();
    }

    private RestTestClient.ResponseSpec suspend(Long memberId, String reason) {
        return client.post().uri(PATH + "/{id}/suspend", memberId)
            .headers(TestMembers.admin(ADMIN_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .body(new MemberSuspendRequest(reason))
            .exchange();
    }

    private RestTestClient.ResponseSpec grantAdmin(Long memberId) {
        return client.post().uri(PATH + "/{id}/grant-admin", memberId)
            .headers(TestMembers.admin(ADMIN_ID))
            .exchange();
    }

    private RestTestClient.ResponseSpec revokeAdmin(Long memberId, Long adminId) {
        return client.post().uri(PATH + "/{id}/revoke-admin", memberId)
            .headers(TestMembers.admin(adminId))
            .exchange();
    }
}
