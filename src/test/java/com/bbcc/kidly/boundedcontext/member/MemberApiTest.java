package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberResponse;
import com.bbcc.kidly.boundedcontext.seller.SellerFixture;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@FunctionalTest
class MemberApiTest {

    private static final String ME_PATH = "/api/v1/members/me";
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private TestTokens tokens;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Test
    @DisplayName("로그인한 회원의 id, 닉네임, 역할을 준다")
    void getMe() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = getMine(tokens.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.id()).isEqualTo(member.getId());
        assertThat(response.nickname()).isEqualTo(MemberFixture.NICKNAME);
        assertThat(response.roles()).containsExactly(Role.BUYER);
        assertThat(response.sellerStatus()).isNull();
        assertThat(response.providers()).containsExactly("KAKAO");
    }

    @Test
    @DisplayName("역할은 토큰이 아니라 DB의 현재 값으로 계산한다")
    void rolesComeFromDatabase() {
        var member = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE));

        var response = getMine(tokens.buyer(member.getId()))
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

        var response = getMine(tokens.buyer(member.getId()))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.roles()).containsExactly(Role.BUYER);
        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("없는 회원이면 404다")
    void missingMember() {
        getMine(tokens.buyer(MISSING_ID))
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void withoutToken() {
        getMine(null)
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("닉네임을 바꾸면 200과 바뀐 닉네임, 로그인 제공자를 주고 DB에도 반영한다")
    void updateNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest("하은맘"))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.nickname()).isEqualTo("하은맘");
        assertThat(response.providers()).containsExactly("KAKAO");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("하은맘");
    }

    @Test
    @DisplayName("앞뒤 공백은 빼고 저장한다")
    void updateNicknameStripsWhitespace() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        var response = patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest("  하은맘  "))
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

        patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest(nickname))
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("잘못된 요청입니다");
    }

    @Test
    @DisplayName("닉네임이 없으면 400")
    void updateWithoutNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());

        patchMine(tokens.buyer(member.getId()), "{}")
            .expectStatus().isBadRequest()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody().jsonPath("$.title").isEqualTo("잘못된 요청입니다");
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임이면 409")
    void updateToUsedNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        memberRepository.save(MemberFixture.kakaoMember("kakao-2"));

        patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest("회원-kakao-2"))
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

        var response = patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest(MemberFixture.NICKNAME))
            .expectStatus().isOk()
            .returnResult(MemberResponse.class)
            .getResponseBody();

        assertThat(response.nickname()).isEqualTo(MemberFixture.NICKNAME);
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname())
            .isEqualTo(MemberFixture.NICKNAME);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 판매자 신청의 신청 회원 닉네임도 바뀐다")
    void updateNicknameChangesSellerApplicationNickname() {
        var member = memberRepository.save(MemberFixture.kakaoMember());
        var applicationId = sellerApplicationRepository.save(SellerFixture.application(member.getId())).getId();

        patchMine(tokens.buyer(member.getId()), new MemberUpdateRequest("하은맘"))
            .expectStatus().isOk();

        // 리스너는 응답 전에 커밋 뒤 같은 스레드에서 돌아서 기다리지 않아도 된다
        assertThat(sellerApplicationRepository.getById(applicationId).getApplicantNickname()).isEqualTo("하은맘");
    }

    @Test
    @DisplayName("닉네임을 바꿀 때 로그인하지 않으면 401")
    void updateWithoutToken() {
        patchMine(null, new MemberUpdateRequest("하은맘"))
            .expectStatus().isUnauthorized();
    }

    private RestTestClient.ResponseSpec patchMine(String token, Object body) {
        return client.patch().uri(ME_PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange();
    }

    private RestTestClient.ResponseSpec getMine(String token) {
        return client.get().uri(ME_PATH)
            .headers(headers -> {
                if (token != null) {
                    headers.setBearerAuth(token);
                }
            })
            .exchange();
    }
}
