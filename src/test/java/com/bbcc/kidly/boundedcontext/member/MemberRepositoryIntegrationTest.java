package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class MemberRepositoryIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("소셜 계정으로 회원을 찾는다")
    void findBySocialAccount() {
        Member saved = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThat(memberRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-1"))
            .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(saved.getId()));
    }

    @Test
    @DisplayName("연결된 소셜 계정이 없으면 찾지 못한다")
    void notFound() {
        memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThat(memberRepository.findBySocialAccount(OAuthProvider.KAKAO, "kakao-2")).isEmpty();
    }

    @Test
    @DisplayName("같은 소셜 계정은 두 회원에게 연결할 수 없다")
    void duplicateSocialAccount() {
        memberRepository.save(MemberFixture.kakaoMember("kakao-1"));

        assertThatThrownBy(() -> memberRepository.save(MemberFixture.kakaoMember("kakao-1")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("판매자 상태 복사본은 없으면 null로, 있으면 같은 값으로 다시 읽는다")
    void sellerStatusMapping() {
        var plain = memberRepository.save(MemberFixture.kakaoMember("kakao-1"));
        var seller = MemberFixture.kakaoMember("kakao-2");
        seller.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);
        seller = memberRepository.save(seller);

        assertThat(memberRepository.findById(plain.getId()))
            .hasValueSatisfying(found -> assertThat(found.getSellerState()).isNull());
        assertThat(memberRepository.findById(seller.getId())).hasValueSatisfying(found -> {
            assertThat(found.getSellerState().status()).isEqualTo(SellerStatus.ACTIVE);
            assertThat(found.getSellerState().changedAt()).isEqualTo(MemberFixture.NOW);
        });
    }
}
