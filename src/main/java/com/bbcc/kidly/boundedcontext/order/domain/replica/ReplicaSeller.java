package com.bbcc.kidly.boundedcontext.order.domain.replica;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 복사본. seller.changed의 지금 상호명 · 상태를 덮어쓴다. 화면에 상호명을 보여 주는 데만 쓴다.
 */
@Entity
@Table(schema = "orders", name = "replica_seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaSeller extends BaseTimeEntity {

    /**
     * 복사본 입력 값.
     *
     * @param status 판매자 상태 이름(다른 도메인 값이라 문자열)
     * @param occurredAt 이벤트 발생 시각. 저장된 값보다 늦을 때만 반영한다
     */
    public record Values(
        Long sellerId,
        String businessName,
        String status,
        LocalDateTime occurredAt) {
    }

    // 판매자 ID를 그대로 키로 쓴다
    @Id
    private Long sellerId;

    @Column(nullable = false, length = 50)
    private String businessName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime eventOccurredAt;

    public static ReplicaSeller create(Values values) {
        ReplicaSeller replica = new ReplicaSeller();
        replica.sellerId = values.sellerId();
        replica.apply(values);
        return replica;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓴다.
     *
     * @return 반영했으면 true
     */
    public boolean applyIfNewer(Values values) {
        if (!values.occurredAt().isAfter(eventOccurredAt)) {
            return false;
        }
        apply(values);
        return true;
    }

    private void apply(Values values) {
        businessName = values.businessName();
        status = values.status();
        eventOccurredAt = values.occurredAt();
    }
}
