package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.SocialAccountFixture;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.WithdrawalBlocker;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberResponse;
import com.bbcc.kidly.member.support.security.TestMembers;
import com.bbcc.kidly.shared.auth.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.FakeSellerWithdrawalBlockerFinder;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class MemberApiTest {

    private static final String ME_PATH = "/api/v1/members/me";
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private FakeSellerWithdrawalBlockerFinder blockerFinder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearBlockerFinder() {
        blockerFinder.clear();
    }

    @Test
    @DisplayName("로그인한 회원의 id, 닉네임, 이메일, 역할, 로그인 제공자를 준다")
    void getMe() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        socialAccountRepository.save(SocialAccountFixture.linked(member.getId(), "kakao-1"));

        var response = getMine(TestMembers.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.id()).isEqualTo(member.getId());
        assertThat(response.nickname()).isEqualTo(MemberFixture.NICKNAME);
        assertThat(response.email()).isEqualTo(MemberFixture.EMAIL);
        assertThat(response.roles()).containsExactly(Role.BUYER);
        assertThat(response.sellerStatus()).isNull();
        assertThat(response.providers()).containsExactly("KAKAO");
    }

    @Test
    @DisplayName("연결된 소셜 계정이 없으면 providers는 빈 목록이다")
    void getMeWithoutSocialAccount() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = getMine(TestMembers.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.providers()).isEmpty();
    }

    @Test
    @DisplayName("이메일을 받기 전에 가입한 회원은 email이 null이다")
    void getMeWithoutEmail() {
        var member = memberRepository.save(MemberFixture.withoutEmail("kakao-1"));

        getMine(TestMembers.buyer(member.getId()))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.email").value(email -> assertThat(email).isNull());
    }

    @Test
    @DisplayName("역할은 토큰이 아니라 DB의 현재 값으로 계산한다")
    void rolesComeFromDatabase() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE));

        var response = getMine(TestMembers.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.roles()).containsExactly(Role.BUYER, Role.SELLER);
        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("정지된 판매자는 sellerStatus가 SUSPENDED이고 SELLER 역할이 없다")
    void suspendedSeller() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.SUSPENDED));

        var response = getMine(TestMembers.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.roles()).containsExactly(Role.BUYER);
        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("없는 회원이면 404다")
    void missingMember() {
        getMine(TestMembers.buyer(MISSING_ID))
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        getMine(TestMembers.anonymous())
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("닉네임을 바꾸면 200과 바뀐 닉네임, 이메일, 로그인 제공자를 주고 DB에도 반영한다")
    void updateNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        socialAccountRepository.save(SocialAccountFixture.linked(member.getId(), "kakao-1"));

        var response = patchMine(TestMembers.buyer(member.getId()), new MemberUpdateRequest("하은맘"))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.nickname()).isEqualTo("하은맘");
        assertThat(response.email()).isEqualTo(MemberFixture.EMAIL);
        assertThat(response.providers()).containsExactly("KAKAO");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("하은맘");
    }

    @Test
    @DisplayName("앞뒤 공백은 빼고 저장한다")
    void updateNicknameStripsWhitespace() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = patchMine(TestMembers.buyer(member.getId()), new MemberUpdateRequest("  하은맘  "))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.nickname()).isEqualTo("하은맘");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("하은맘");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"가", "가나다라마바사아자차카타파하가나다라마바사", "   "})
    @DisplayName("닉네임이 2자 미만이거나 20자를 넘거나 비어 있으면 400")
    void updateWithInvalidNickname(String nickname) {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        patchMine(TestMembers.buyer(member.getId()), new MemberUpdateRequest(nickname))
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("잘못된 요청입니다");
    }

    @Test
    @DisplayName("닉네임이 없으면 400")
    void updateWithoutNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        patchMine(TestMembers.buyer(member.getId()), "{}")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("잘못된 요청입니다");
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임이면 409")
    void updateToUsedNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        memberRepository.save(MemberFixture.kakaoMember("kakao-2"));

        patchMine(TestMembers.buyer(member.getId()), new MemberUpdateRequest("회원-kakao-2"))
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("이미 사용 중인 닉네임입니다");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname())
            .isEqualTo(MemberFixture.NICKNAME);
    }

    @Test
    @DisplayName("지금 닉네임 그대로 보내면 200")
    void updateToSameNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = patchMine(TestMembers.buyer(member.getId()), new MemberUpdateRequest(MemberFixture.NICKNAME))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.nickname()).isEqualTo(MemberFixture.NICKNAME);
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname())
            .isEqualTo(MemberFixture.NICKNAME);
    }

    @Test
    @DisplayName("닉네임을 바꿀 때 로그인하지 않으면 401")
    void updateWithoutToken() {
        patchMine(TestMembers.anonymous(), new MemberUpdateRequest("하은맘"))
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("탈퇴하면 204이고 회원은 WITHDRAWN이 된다. 인증이 소유한 Refresh Token 쿠키는 건드리지 않는다")
    void withdraw() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        deleteMine(TestMembers.buyer(member.getId()))
            .expectStatus().isNoContent()
            .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getStatus())
            .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(blockerFinder.called(member.getId())).isFalse();
    }

    @Test
    @DisplayName("판매자의 남은 주문은 탈퇴를 막는다")
    void sellerWithdrawalBlocked() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE));
        blockerFinder.setBlockers(member.getId(), List.of(new WithdrawalBlocker("UNCONFIRMED_ORDER_ITEM", 3)));
        deleteMine(TestMembers.buyer(member.getId())).expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody().jsonPath("$.detail")
            .value(detail -> assertThat(detail.toString()).contains("구매확정 전 주문상품 3건"));
        assertThat(memberRepository.getById(member.getId()).getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(withdrawalEventCount(member.getId())).isZero();
    }

    @Test
    @DisplayName("판매자 탈퇴 조건 조회에 실패하면 탈퇴하지 않는다")
    void sellerWithdrawalCheckFails() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.SUSPENDED));
        blockerFinder.fail(member.getId());
        deleteMine(TestMembers.buyer(member.getId())).expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(memberRepository.getById(member.getId()).getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(withdrawalEventCount(member.getId())).isZero();
    }

    @Test
    @DisplayName("남은 항목이 없는 판매자는 탈퇴한다")
    void sellerWithdraws() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE));
        deleteMine(TestMembers.buyer(member.getId())).expectStatus().isNoContent();
        assertThat(memberRepository.getById(member.getId()).getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(blockerFinder.called(member.getId())).isTrue();
        assertThat(withdrawalEventCount(member.getId())).isEqualTo(1);
    }

    private int withdrawalEventCount(Long memberId) {
        return jdbcTemplate.queryForObject("select count(*) from member.member_outbox "
            + "where event_type = 'member.withdrawn' and message_key = ?", Integer.class, memberId.toString());
    }

    @Test
    @DisplayName("탈퇴할 때 로그인하지 않으면 401")
    void withdrawWithoutToken() {
        deleteMine(TestMembers.anonymous())
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec deleteMine(Consumer<HttpHeaders> login) {
        return client.delete().uri(ME_PATH)
            .headers(login)
            .exchange();
    }

    private RestTestClient.ResponseSpec patchMine(Consumer<HttpHeaders> login, Object body) {
        return client.patch().uri(ME_PATH)
            .headers(login)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private RestTestClient.ResponseSpec getMine(Consumer<HttpHeaders> login) {
        return client.get().uri(ME_PATH)
            .headers(login)
            .exchange();
    }
}
