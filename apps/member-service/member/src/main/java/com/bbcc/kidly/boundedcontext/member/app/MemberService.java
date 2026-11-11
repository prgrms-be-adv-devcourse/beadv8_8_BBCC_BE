package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberResult;
import com.bbcc.kidly.boundedcontext.member.app.dto.MemberUpdateRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.SellerWithdrawalBlockerFinder;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberPolicy;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import com.bbcc.kidly.boundedcontext.member.domain.NicknamePolicy;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.out.MemberTokenRevoker;
import com.bbcc.kidly.shared.auth.out.SocialAccountFinder;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 본인의 정보를 조회 · 수정하고 탈퇴한다. 역할은 토큰이 아니라 DB의 현재 값으로 계산하므로 판매자 승인 직후 재발급 전에도 SELLER가 보인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final NicknamePolicy nicknamePolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberTokenRevoker memberTokenRevoker;
    private final SocialAccountFinder socialAccountFinder;
    private final SellerWithdrawalBlockerFinder blockerFinder;

    @Transactional(readOnly = true)
    public MemberResult getMine(Long memberId) {
        return MemberResult.from(memberRepository.getById(memberId), socialAccountFinder.findProviders(memberId));
    }

    // 지금 닉네임과 같으면 바꾸지 않고 이벤트도 발행하지 않는다
    // 동시에 같은 닉네임으로 바꾸면 유니크 제약이 한 요청만 통과시킨다 (나머지는 커밋 때 409)
    @Transactional
    public MemberResult update(Long memberId, MemberUpdateRequest request) {
        var member = memberRepository.getById(memberId);
        if (nicknamePolicy.changeNickname(member, new Nickname(request.nickname()))) {
            eventPublisher.publishEvent(new MemberChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new MemberChangedEvent.Data(memberId, member.getNickname())));
            log.info("닉네임 변경: memberId={}", memberId);
        }
        return MemberResult.from(member, socialAccountFinder.findProviders(memberId));
    }

    // 배송지 · 자녀 프로필 · 소셜 계정 연결은 탈퇴 이벤트를 받아 지운다
    // Refresh Token은 탈퇴 회원의 재발급이 막히므로 남겨 두고, 만료되면 RefreshTokenCleaner가 지운다
    @Transactional
    public void withdraw(Long memberId) {
        var now = LocalDateTime.now();
        var member = memberRepository.getById(memberId);
        if (member.requiresSellerWithdrawalCheck()) {
            var blockers = blockerFinder.find(memberId);
            if (!blockers.isEmpty()) {
                throw new BusinessException(MemberErrorCode.SELLER_WITHDRAWAL_BLOCKED,
                    MemberPolicy.withdrawalBlockedDetail(blockers));
            }
        }
        member.withdraw(now);
        memberTokenRevoker.revoke(memberId, now);
        eventPublisher.publishEvent(
            new MemberWithdrawnEvent(UUID.randomUUID(), now, new MemberWithdrawnEvent.Data(memberId)));
        log.info("회원 탈퇴: memberId={}", memberId);
    }
}
