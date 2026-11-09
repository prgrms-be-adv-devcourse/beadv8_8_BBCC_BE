package com.bbcc.kidly.boundedcontext.notification.in.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문이 아직 보내지 않는 이벤트(#1018)를 알림이 읽는 모양. 주문에 요청한 shared 레코드와 칸 이름이 같아,
 * 주문이 그대로 보내기 시작하면 알림을 다시 배포하지 않아도 받는다. 모르는 칸은 무시한다.
 * 주문이 shared.order.event에 레코드를 만들면 리스너가 그 레코드를 읽게 바꾸고 이 파일을 지운다.
 */
final class AwaitedOrderEvents {

    private AwaitedOrderEvents() {
    }

    /**
     * wish.paid · wish.expired. items는 둘 다 조른 상품이다(일부만 결제해도 결제 완료).
     */
    record WishClosed(Data data) {

        record Data(Long wishRequestId, Long childId, List<Item> items) {
        }

        record Item(Long productId) {
        }
    }

    /**
     * order-item.auto-confirm-scheduled.
     */
    record AutoConfirmScheduled(Data data) {

        record Data(Long orderItemId, Long orderId, Long memberId, Long productId, int quantity,
            LocalDateTime confirmAt) {
        }
    }
}
