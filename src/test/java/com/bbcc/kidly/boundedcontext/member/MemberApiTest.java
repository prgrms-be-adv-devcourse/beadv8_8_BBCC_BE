package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.in.dto.MemberResponse;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.FunctionalTest;
import com.bbcc.kidly.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
