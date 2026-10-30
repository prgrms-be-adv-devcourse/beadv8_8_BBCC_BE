package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.SellerChangedCommand;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * seller.changed를 받아 판매자 복사본 반영을 맡긴다. 이벤트 필드가 바뀌면 여기 변환만 고친다.
 * 받는 곳(#541): Kafka 리스너({@link SellerEventKafkaListener}), 스위치가 꺼졌을 때 Spring 수신({@link SpringEventReceiver}).
 * 처리 메서드는 부르는 쪽 트랜잭션에 합류하고(Spring 수신), 없으면 새로 연다(Kafka 리스너).
 * 리스너는 재시도가 없어 저장이 실패하면 이벤트가 사라지므로, 깨진 값은 여기서 걸러 예외 없이 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SellerReplicaEventListener {

    private final ReplicaService replicaService;

    /**
     * 판매자 ID · 발생 시각 · 상호명 · 상태 중 하나라도 없으면 무시한다(기존 복사본을 덮지 않음).
     * 상태는 판매자 도메인 enum이라 이름 문자열로 넘긴다.
     */
    @Transactional
    public void onSellerChanged(SellerChangedEvent event) {
        Long sellerId = event.data() == null ? null : event.data().sellerId();
        if (!EventGuard.accept("판매자", event, "sellerId", sellerId, missingValue(event))) {
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
