package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.out.MemberQueryRepository;
import com.bbcc.kidly.boundedcontext.member.out.MemberRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class MemberRepositoryIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberQueryRepository memberQueryRepository;

    @Test
    @DisplayName("소셜 계정으로 회원을 찾는다")
    void findBySocialAccount() {
        Member saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThat(memberQueryRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-1"))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(saved.getId()));
    }

    @Test
    @DisplayName("연결된 소셜 계정이 없으면 찾지 못한다")
    void notFound() {
        memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThat(memberQueryRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-2")).isEmpty();
    }

    @Test
    @DisplayName("같은 소셜 계정은 두 회원에게 연결할 수 없다")
    void duplicateSocialAccount() {
        memberRepository.saveAndFlush(MemberFixture.kakaoMember("kakao-1"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(MemberFixture.kakaoMember("kakao-1")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
