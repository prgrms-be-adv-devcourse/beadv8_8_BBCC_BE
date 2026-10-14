package com.bbcc.kidly.boundedcontext.recommend.app.purchase;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseRecordService {

    private final PurchaseRecordRepository purchaseRecordRepository;

    /**
     * 구매확정 이벤트의 값을 구매 기록으로 저장한다. 기록할 구매확정인지는 구매 기록이 판단하고, 아니면 건너뛴다.
     */
    public void recordPurchaseConfirmed(OrderItemPurchaseConfirmedEvent event) {
        PurchaseRecord.RecordInfo info = toRecordInfo(event);
        if (!PurchaseRecord.isRecordable(info)) {
            return;
        }
        purchaseRecordRepository.save(PurchaseRecord.create(info));
    }

    // 구매 기록의 칸은 원래 표의 칸 이름을 따르고 이벤트는 계약 이름을 쓰므로 여기서 이름을 옮긴다
    private PurchaseRecord.RecordInfo toRecordInfo(OrderItemPurchaseConfirmedEvent event) {
        OrderItemPurchaseConfirmedEvent.Data data = event.data();
        return new PurchaseRecord.RecordInfo(
            data.orderItemId(),
            data.orderId(),
            data.memberId(),
            data.childId(),
            data.productId(),
            data.optionId(),
            data.size(),
            data.confirmedAt());
    }
}
