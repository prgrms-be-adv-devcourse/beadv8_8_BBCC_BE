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
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
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
    @DisplayName("member.roles()와 닉네임을 그대로 issuer에 넘긴다")
    void issuesWithMemberRoles() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        member.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        authTokenService.issueAccessToken(MEMBER_ID);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, member.roles());
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
