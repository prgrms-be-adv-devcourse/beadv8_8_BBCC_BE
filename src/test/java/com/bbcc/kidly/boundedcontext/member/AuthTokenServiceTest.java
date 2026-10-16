package com.bbcc.kidly.boundedcontext.member;

import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @InjectMocks
    private AuthTokenService authTokenService;

    @Test
    @DisplayName("member.roles()와 닉네임을 그대로 issuer에 넘긴다")
    void issuesWithMemberRoles() {
        var member = MemberFixture.withRole(MemberRole.ADMIN);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        member.applySellerState(SellerStatus.ACTIVE, MemberFixture.NOW);

        authTokenService.issueAccessToken(member);

        then(accessTokenIssuer).should().issue(MEMBER_ID, MemberFixture.NICKNAME, member.roles());
    }
}
