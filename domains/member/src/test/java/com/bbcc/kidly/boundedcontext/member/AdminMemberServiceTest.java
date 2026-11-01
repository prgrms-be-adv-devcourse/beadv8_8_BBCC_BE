package com.bbcc.kidly.boundedcontext.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정지 · 관리자 해제는 이미 발급된 Access Token을 무효화한다. 무효화(member.tokens-revoked 발행)를 Gateway가 받아 그 토큰을 거절한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ADMIN_ID = 100L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSummaryQuery memberSummaryQuery;

    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("정지하면 그 회원의 Access Token을 무효화한다")
    void suspendRevokesAccessToken() {
        given(memberRepository.getById(MEMBER_ID)).willReturn(MemberFixture.kakaoMember());

        adminMemberService.suspend(MEMBER_ID, ADMIN_ID, new MemberSuspendRequest("신고 접수"));

        then(accessTokenRevocationService).should().revoke(eq(MEMBER_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("관리자를 해제하면 그 회원의 Access Token을 무효화한다")
    void revokeAdminRevokesAccessToken() {
        given(memberRepository.getById(MEMBER_ID)).willReturn(MemberFixture.withRole(MemberRole.ADMIN));

        adminMemberService.revokeAdmin(MEMBER_ID, ADMIN_ID);

        then(accessTokenRevocationService).should().revoke(eq(MEMBER_ID), any(LocalDateTime.class));
    }
}
