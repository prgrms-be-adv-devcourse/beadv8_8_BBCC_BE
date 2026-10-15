package com.bbcc.kidly.boundedcontext.settlement.app;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemCreateInfo;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정된 주문상품을 정산 대상으로 쌓는다. 판매금액은 주문상품 금액(단가 × 수량)이다.
 */
@Service
@RequiredArgsConstructor
public class SettlementItemService {

    private final SettlementItemRepository settlementItemRepository;

    // 같은 주문상품이 다시 오면 건너뛴다. 동시에 들어온 중복은 order_item_id 유니크가 막는다
    @Transactional
    public void register(OrderItemPurchaseConfirmedEvent.Data data) {
        if (settlementItemRepository.existsByOrderItemId(data.orderItemId())) {
            return;
        }
        settlementItemRepository.save(SettlementItem.create(new SettlementItemCreateInfo(
            data.orderId(), data.orderItemId(), data.sellerId(), data.amount(), data.confirmedAt())));
    }
}
