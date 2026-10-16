package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.member.event.MemberRegisteredEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 가입하면 잔액 0인 예치금 지갑을 만든다. 같은 회원의 이벤트를 다시 받거나 동시에 받아도 지갑은 하나다.
 * 이 리스너가 실패해도 지갑을 쓰는 곳(충전 요청 · 결제 · 입금)이 없으면 만든다.
 */
@Component
@RequiredArgsConstructor
public class MemberRegisteredListener {

    private final WalletRepository walletRepository;

    @DomainEventListener
    public void on(MemberRegisteredEvent event) {
        walletRepository.createIfAbsent(event.data().memberId(), LocalDateTime.now());
    }
}
