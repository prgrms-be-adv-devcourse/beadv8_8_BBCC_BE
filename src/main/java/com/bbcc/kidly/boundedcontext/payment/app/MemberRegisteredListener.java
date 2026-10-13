package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 가입하면 잔액 0인 예치금 지갑을 만든다.
 * 같은 회원의 이벤트를 다시 받으면 건너뛴다. 동시에 받아 확인을 통과해도 {@code wallet.member_id} UK가 막는다.
 */
@Component
@RequiredArgsConstructor
public class MemberRegisteredListener {

    private final WalletRepository walletRepository;

    @DomainEventListener
    public void on(MemberRegisteredEvent event) {
        Long memberId = event.data().memberId();
        if (walletRepository.existsByMemberId(memberId)) {
            return;
        }
        walletRepository.save(Wallet.create(memberId));
    }
}
