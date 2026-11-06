package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.app.dto.PurchaseConfirmedCommand;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxNotificationInfo;
import com.bbcc.kidly.boundedcontext.notification.domain.InboxRecipient;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 구매확정을 구매자 알림함에 넣는다. 다음에 들어왔을 때 알면 되는 일이라 메일은 보내지 않는다.
 * 직접 확정은 구매자가 그 화면에서 결과를 보므로 넣지 않고, 확정 방식이 없는 예전 메시지도 넣지 않는다.
 * 누르면 그 주문 상세로 간다.
 */
@Service
@RequiredArgsConstructor
public class AutoConfirmInboxService {

    private static final String AUTO = "AUTO";

    private final InboxNotificationService inboxNotificationService;
    private final ProductNameService productNameService;

    // 상품 이름 복사본이 없으면(아직 상품 이벤트를 받지 못했다) 이름 없이 쓴다
    @Transactional
    public void addAutoConfirmed(PurchaseConfirmedCommand command) {
        if (!AUTO.equals(command.confirmType())) {
            return;
        }
        var body = productNameService.findName(command.productId())
            .map(name -> "%s %d개가 자동으로 구매확정됐어요.".formatted(name, command.quantity()))
            .orElse("주문상품 %d개가 자동으로 구매확정됐어요.".formatted(command.quantity()));
        inboxNotificationService.add(new InboxNotificationInfo(InboxRecipient.member(command.memberId()),
            NotificationType.PURCHASE_AUTO_CONFIRMED, "구매가 자동으로 확정됐어요", body,
            "/orders/" + command.orderId(), "order-item.auto-confirmed:" + command.orderItemId()));
    }
}
