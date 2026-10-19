package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.NicknamePolicy;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 정보를 조회 · 수정하고 탈퇴한다. 역할은 토큰이 아니라 DB의 현재 값으로 계산하므로 판매자 승인 직후 재발급 전에도 SELLER가 보인다.
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

    // 신청하면 바로 탈퇴한다. 배송지 · 자녀 프로필은 탈퇴 이벤트를 받아 지운다
    // Refresh Token은 탈퇴 회원의 재발급이 막히므로 남겨 두고, 만료되면 RefreshTokenCleaner가 지운다
    // TODO 예치금 잔액이 0원인지 확인한다 (결제 도메인)
    // TODO 진행 중인 주문이 없는지 확인한다 (주문 도메인)
    // TODO 판매자인 회원의 판매자 · 상품을 처리한다 (판매자 · 상품 도메인)
    // TODO 이미 발급된 Access Token을 즉시 무효화한다. 지금은 만료될 때까지 쓸 수 있다
    @Transactional
    public void withdraw(Long memberId) {
        var now = LocalDateTime.now();
        var member = memberRepository.getById(memberId);
        member.withdraw(now);
        eventPublisher.publish(
            new MemberWithdrawnEvent(UUID.randomUUID(), now, new MemberWithdrawnEvent.Data(memberId)));
    }
}
