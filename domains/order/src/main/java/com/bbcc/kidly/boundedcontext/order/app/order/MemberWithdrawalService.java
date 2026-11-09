package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.port.CartOwnerCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 정리(#1001, member.withdrawn): 결제대기 주문 취소(order.cancelled) · 회원 장바구니(주인 MEMBER) 비우기.
 * 결제 · 배송 중 주문은 그대로 둔다. 자녀 장바구니 · 조르기는 자녀 삭제 이벤트(child.changed)로 정리된다(자녀 프로필 도메인이 탈퇴를
 * 받아 자녀마다 삭제를 낸다). 한 트랜잭션이라 실패하면 함께 롤백되고 수신 쪽이 재시도한다. 탈퇴는 되돌지 않으므로 여러 번 와도 같다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final OrderService orderService;
    private final CartOwnerCleaner cartOwnerCleaner;

    public void withdraw(Long memberId) {
        int orders = orderService.cancelPendingForMemberWithdrawal(memberId);
        int cartItems = cartOwnerCleaner.removeAll(CartOwnerCleaner.OwnerType.MEMBER, memberId);
        log.info("회원 탈퇴 정리: memberId={} cancelledOrders={} cartItems={}", memberId, orders, cartItems);
    }
}
