package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.member.app.AdminMemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import com.bbcc.kidly.shared.member.event.MemberSuspendedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("정지하면 그 회원의 Access Token을 무효화한다")
    void suspendRevokesAccessToken() {
        var member = MemberFixture.kakaoMember();
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        adminMemberService.suspend(MEMBER_ID, ADMIN_ID, new MemberSuspendRequest("신고 접수"));

        then(memberTokenRevoker).should().revoke(eq(MEMBER_ID), any(LocalDateTime.class));
        var captor = ArgumentCaptor.forClass(MemberSuspendedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.data().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.data().reason()).isEqualTo("신고 접수");
        assertThat(event.data().suspendedAt()).isEqualTo(event.occurredAt());
        then(memberTokenRevoker).should().revoke(MEMBER_ID, event.data().suspendedAt());
    }

    @Test
    @DisplayName("이미 정지된 회원이면 이벤트를 발행하거나 토큰을 무효화하지 않는다")
    void suspendAlreadySuspended() {
        var member = MemberFixture.kakaoMember();
        member.suspend(ADMIN_ID, "기존 사유", LocalDateTime.now());
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);

        assertThatThrownBy(() -> adminMemberService.suspend(
            MEMBER_ID, ADMIN_ID, new MemberSuspendRequest("새 사유")))
            .isInstanceOf(com.bbcc.kidly.member.support.error.BusinessException.class);
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
        then(memberTokenRevoker).shouldHaveNoInteractions();
    }

}
