package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AdminProperties;
import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.security.Role;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        var adminProperties = new AdminProperties(Set.of(MemberFixture.EMAIL));
        authTokenService = new AuthTokenService(memberRepository, accessTokenIssuer, adminProperties);
    }

    @Test
    @DisplayName("member.roles()와 닉네임을 그대로 issuer에 넘긴다")
    void issuesWithMemberRoles() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        member.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, member.roles());
    }

    @Test
    @DisplayName("이메일이 관리자 목록에 있으면 ADMIN으로 바꾸고 ADMIN 역할로 발급한다")
    void promotesListedEmail() {
        var member = MemberFixture.kakaoMember();
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        authTokenService.issueAccessToken(MEMBER_ID);

        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, List.of(Role.BUYER, Role.ADMIN));
    }

    @Test
    @DisplayName("관리자 목록에서 빠진 ADMIN은 USER로 바꾸고 ADMIN 없이 발급한다")
    void demotesUnlistedAdmin() {
        var member = MemberFixture.withRole("kakao-2", MemberRole.ADMIN);
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        authTokenService.issueAccessToken(MEMBER_ID);

        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        then(accessTokenIssuer).should().issue(MEMBER_ID, member.getNickname(), List.of(Role.BUYER));
    }

    @Test
    @DisplayName("없는 회원이면 발급하지 않는다")
    void memberNotFound() {
        given(memberRepository.getById(MEMBER_ID)).willThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> authTokenService.issueAccessToken(MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        then(accessTokenIssuer).shouldHaveNoInteractions();
    }
}
