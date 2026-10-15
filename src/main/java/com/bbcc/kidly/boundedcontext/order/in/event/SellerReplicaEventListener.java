package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.SellerChangedCommand;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * seller.changed를 받아 판매자 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerReplicaEventListener {

    private final ReplicaService replicaService;

    /**
     * 판매자 ID · 발생 시각 · 상호명 · 상태 중 하나라도 없으면 무시한다(기존 복사본을 덮지 않음).
     * 상태는 판매자 도메인 enum이라 이름 문자열로 넘긴다.
     */
    @DomainEventListener
    public void onSellerChanged(SellerChangedEvent event) {
        String missing = missingValue(event);
        if (missing != null) {
            Long sellerId = event.data() == null ? null : event.data().sellerId();
            log.warn("판매자 이벤트 무시: event=seller.changed sellerId={} reason={}", sellerId, missing);
            return;
        }
        SellerChangedEvent.Data data = event.data();
        replicaService.applySellerChanged(new SellerChangedCommand(
            data.sellerId(), data.businessName(), data.status().name(), event.occurredAt()));
    }

    private static String missingValue(SellerChangedEvent event) {
        SellerChangedEvent.Data data = event.data();
        if (data == null) {
            return "missing-data";
        }
        if (data.sellerId() == null) {
            return "missing-seller-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        if (data.businessName() == null) {
            return "missing-business-name";
        }
        if (data.status() == null) {
            return "missing-status";
        }
        return null;
    }
}
