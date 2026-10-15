package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SettlementItemService {

    private final SettlementItemRepository settlementItemRepository;

    /** 구매확정 이벤트의 값으로 정산항목을 저장한다. 주문상품 금액이 판매금액이 된다. */
    public void registerPurchaseConfirmed(OrderItemPurchaseConfirmedEvent event) {
        OrderItemPurchaseConfirmedEvent.Data data = event.data();
        SettlementItemCreateInfo info = new SettlementItemCreateInfo(
                data.orderId(),
                data.orderItemId(),
                data.sellerId(),
                data.amount(),
                data.confirmedAt());
        settlementItemRepository.save(SettlementItem.create(info));
    }
}
