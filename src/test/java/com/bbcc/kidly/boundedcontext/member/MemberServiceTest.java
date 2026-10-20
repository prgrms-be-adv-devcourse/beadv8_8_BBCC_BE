package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.boundedcontext.member.app.MemberService;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.NicknamePolicy;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, new NicknamePolicy(memberRepository), eventPublisher,
            accessTokenRevocationService);
    }

    @Test
    @DisplayName("닉네임을 바꾸면 변경 이벤트를 발행한다")
    void update() {
        given(memberRepository.getById(MEMBER_ID)).willReturn(MemberFixture.kakaoMember());

        var result = memberService.update(MEMBER_ID, new MemberUpdateRequest("하은맘"));

        assertThat(result.nickname()).isEqualTo("하은맘");
        var captor = ArgumentCaptor.forClass(MemberChangedEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().data()).isEqualTo(new MemberChangedEvent.Data(MEMBER_ID, "하은맘"));
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임이면 바꾸지 않는다")
    void duplicateNickname() {
        var member = MemberFixture.kakaoMember();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        given(memberRepository.getById(MEMBER_ID)).willReturn(member);
        given(memberRepository.existsByNicknameAndIdNot("하은맘", MEMBER_ID)).willReturn(true);

        assertThatThrownBy(() -> memberService.update(MEMBER_ID, new MemberUpdateRequest("하은맘")))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NICKNAME_ALREADY_USED));
        assertThat(member.getNickname()).isEqualTo(MemberFixture.NICKNAME);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("지금 닉네임과 같으면 이벤트를 발행하지 않는다")
    void sameNickname() {
        given(memberRepository.getById(MEMBER_ID)).willReturn(MemberFixture.kakaoMember());

        memberService.update(MEMBER_ID, new MemberUpdateRequest(MemberFixture.NICKNAME));

        then(eventPublisher).shouldHaveNoInteractions();
    }
}
