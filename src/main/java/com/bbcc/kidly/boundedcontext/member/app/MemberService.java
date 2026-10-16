package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.NicknamePolicy;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 정보를 조회 · 수정한다. 역할은 토큰이 아니라 DB의 현재 값으로 계산하므로 판매자 승인 직후 재발급 전에도 SELLER가 보인다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final NicknamePolicy nicknamePolicy;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public MemberResult getMine(Long memberId) {
        return MemberResult.from(memberRepository.getById(memberId));
    }

    // 지금 닉네임과 같으면 바꾸지 않고 이벤트도 발행하지 않는다
    // 동시에 같은 닉네임으로 바꾸면 유니크 제약이 한 요청만 통과시킨다 (나머지는 커밋 때 409)
    @Transactional
    public MemberResult update(Long memberId, MemberUpdateRequest request) {
        var member = memberRepository.getById(memberId);
        if (nicknamePolicy.changeNickname(member, new Nickname(request.nickname()))) {
            eventPublisher.publish(new MemberChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new MemberChangedEvent.Data(memberId, member.getNickname())));
        }
        return MemberResult.from(member);
    }
}
