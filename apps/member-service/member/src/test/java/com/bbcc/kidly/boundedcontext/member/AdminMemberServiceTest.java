package com.bbcc.kidly.boundedcontext.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정지는 이미 발급된 Access Token을 무효화한다. 무효화(auth.tokens-revoked 발행)를 Gateway가 받아 그 토큰을 거절한다.
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
    private MemberTokenRevoker memberTokenRevoker;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("정지하면 그 회원의 Access Token을 무효화한다")
    void suspendRevokesAccessToken() {
        given(memberRepository.getById(MEMBER_ID)).willReturn(MemberFixture.kakaoMember());

        adminMemberService.suspend(MEMBER_ID, ADMIN_ID, new MemberSuspendRequest("신고 접수"));

        then(memberTokenRevoker).should().revoke(eq(MEMBER_ID), any(LocalDateTime.class));
    }

}
