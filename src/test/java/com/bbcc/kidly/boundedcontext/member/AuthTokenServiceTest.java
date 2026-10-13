package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @InjectMocks
    private AuthTokenService authTokenService;

    @Test
    @DisplayName("일반 회원은 BUYER 역할로 발급한다")
    void buyer() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.kakaoMember()));

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER));
    }

    @Test
    @DisplayName("관리자는 BUYER와 ADMIN 역할로 발급한다")
    void admin() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(MemberFixture.withRole(MemberRole.ADMIN)));

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER, Role.ADMIN));
    }

    @Test
    @DisplayName("ACTIVE 판매자는 BUYER와 SELLER 역할로 발급한다")
    void seller() {
        given(memberRepository.findById(MEMBER_ID))
            .willReturn(Optional.of(MemberFixture.withSellerState(SellerStatus.ACTIVE)));

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER, Role.SELLER));
    }

    @Test
    @DisplayName("정지된 판매자는 SELLER 역할 없이 발급한다")
    void suspendedSeller() {
        given(memberRepository.findById(MEMBER_ID))
            .willReturn(Optional.of(MemberFixture.withSellerState(SellerStatus.SUSPENDED)));

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER));
    }

    @Test
    @DisplayName("판매자인 관리자는 BUYER, SELLER, ADMIN 순서로 발급한다")
    void sellerAdmin() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        member.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should()
            .issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER, Role.SELLER, Role.ADMIN));
    }

    @Test
    @DisplayName("없는 회원이면 발급하지 않는다")
    void memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.issueAccessToken(MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }
}
