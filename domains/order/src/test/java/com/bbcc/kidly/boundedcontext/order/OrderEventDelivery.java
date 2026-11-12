package com.bbcc.kidly.boundedcontext.order;

import com.bbcc.kidly.boundedcontext.order.app.order.SellerUnavailableCleanupService;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildAccountEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.ChildProfileEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.MemberEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.PaymentEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.SellerReplicaEventListener;
import com.bbcc.kidly.boundedcontext.order.in.event.StockEventListener;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 테스트가 다른 도메인 이벤트를 주문에 넣는 곳(#532). 이벤트 수신 방식이 바뀌면 이 클래스만 고친다.
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 모든 통합 · 기능 테스트 컨텍스트에 똑같이 등록된다(OrderFlowEventRecorder와 같다).
 */
@Component
@RequiredArgsConstructor
public class OrderEventDelivery {

    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final PaymentEventListener paymentEventListener;
    private final StockEventListener stockEventListener;
    private final SellerReplicaEventListener sellerReplicaEventListener;
    private final ChildProfileEventListener childProfileEventListener;
    private final ChildAccountEventListener childAccountEventListener;
    private final MemberEventListener memberEventListener;
    private final SellerUnavailableCleanupService sellerUnavailableCleanupService;

    /**
     * 운영과 같은 길로 보낸다: 한 트랜잭션에서 발행하고 커밋된 뒤 리스너가 받는다(여러 개면 준 순서대로).
     * 리스너 예외는 받는 쪽에서 로그로만 남으므로 결과는 저장된 상태로 확인한다.
     * 판매자 · 자녀 · 자녀 계정 · 회원 탈퇴(#1001) 이벤트는 다른 서비스가 발행해 Kafka로만 받으므로, Kafka 리스너가 부르는 처리 메서드를 그 트랜잭션 안에서 부른다.
     * 판매자 이벤트는 Kafka 리스너처럼 트랜잭션이 커밋된 뒤 판매 불가 판매자 정리(#1121, 주문마다 트랜잭션)를 부른다.
     */
    public void deliver(DomainEvent... events) {
        List<Long> sellerIds = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            for (DomainEvent event : events) {
                switch (event) {
                    case SellerChangedEvent seller -> {
                        sellerReplicaEventListener.onSellerChanged(seller);
                        if (seller.data() != null) {
                            sellerIds.add(seller.data().sellerId());
                        }
                    }
                    case ChildChangedEvent child -> childProfileEventListener.onChildChanged(child);
                    case ChildAccountChangedEvent account -> childAccountEventListener.onChildAccountChanged(account);
                    case MemberWithdrawnEvent withdrawn -> memberEventListener.onMemberWithdrawn(withdrawn);
                    default -> eventPublisher.publish(event);
                }
            }
        });
        sellerIds.forEach(sellerUnavailableCleanupService::cleanUpIfUnavailable);
    }

    /**
     * 받는 리스너 메서드를 바로 불러 리스너 예외를 그대로 돌려받는다(실패 분류 · 보관 · 동시 처리 확인용).
     */
    public void invoke(DomainEvent event) {
        dispatch(event, paymentEventListener, stockEventListener);
    }

    /**
     * 리스너 트랜잭션 없이 받는다(트랜잭션 프록시를 벗긴 리스너를 부른다). 수신 방식이 리스너 트랜잭션을 열지 않아도
     * (예: 메시지 브로커) 결과 · 실패 분류 · 보관이 같은지 확인하는 용도(#532).
     */
    public void invokeWithoutListenerTransaction(DomainEvent event) {
        dispatch(event, AopTestUtils.getTargetObject(paymentEventListener),
            AopTestUtils.getTargetObject(stockEventListener));
    }

    private static void dispatch(DomainEvent event, PaymentEventListener payment, StockEventListener stock) {
        switch (event) {
            case PaymentCompletedEvent completed -> payment.onPaymentCompleted(completed);
            case PaymentFailedEvent failed -> payment.onPaymentFailed(failed);
            case StockDeductedEvent deducted -> stock.onStockDeducted(deducted);
            case StockDeductionFailedEvent failed -> stock.onStockDeductionFailed(failed);
            default -> throw new IllegalArgumentException("주문이 받지 않는 이벤트: " + event.getClass().getSimpleName());
        }
    }
}
