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
