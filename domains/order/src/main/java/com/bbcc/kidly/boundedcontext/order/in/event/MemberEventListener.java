package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.order.MemberWithdrawalService;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * member.withdrawn을 받아 회원 탈퇴 정리를 맡긴다(#1001). 받는 곳: Kafka 리스너({@link MemberEventKafkaListener})뿐이다(판매자 ·
 * 자녀 계정과 같음). 처리 메서드는 트랜잭션을 연다. 회원 ID가 없으면 무시한다(기존 값을 건드리지 않음).
 */
@Component
@RequiredArgsConstructor
public class MemberEventListener {

    private final MemberWithdrawalService memberWithdrawalService;

    @Transactional
    public void onMemberWithdrawn(MemberWithdrawnEvent event) {
        Long memberId = event.data() == null ? null : event.data().memberId();
        if (!EventGuard.accept("회원 탈퇴", event, "memberId", memberId,
            memberId == null ? (event.data() == null ? "missing-data" : "missing-member-id") : null)) {
            return;
        }
        memberWithdrawalService.withdraw(memberId);
    }
}
