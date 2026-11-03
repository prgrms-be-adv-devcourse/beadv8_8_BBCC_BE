package com.bbcc.kidly.boundedcontext.order.domain.replica;

import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 복사본. child.changed의 지금 값을 덮어쓴다. 삭제 이벤트가 와도 행을 지우지 않고 삭제 표시만 남긴다.
 */
@Entity
@Table(schema = "orders", name = "replica_child")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaChild extends OrderTimeEntity {

    /**
     * 복사본 입력 값.
     *
     * @param name 자녀 이름. 이벤트에 아직 없어서 null
     * @param bodyType 체형 이름. 입력하지 않았으면 null
     * @param occurredAt 이벤트 발생 시각. 저장된 값보다 늦을 때만 반영한다
     */
    public record Values(
        Long childId,
        Long memberId,
        String name,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String bodyType,
        boolean deleted,
        LocalDateTime occurredAt) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long childId;

    @Column(nullable = false)
    private Long memberId;

    @Column(length = 50)
    private String name;

    @Column(precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(length = 20)
    private String bodyType;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private LocalDateTime eventOccurredAt;

    public static ReplicaChild create(Values values) {
        ReplicaChild replica = new ReplicaChild();
        replica.childId = values.childId();
        replica.apply(values);
        return replica;
    }

    /**
     * 프로필을 모르는 삭제도 ID · 보호자 · 삭제 시각을 남긴다. 프로필 없는 활성 행은 DB 제약으로 허용하지 않는다.
     */
    public static ReplicaChild createDeleted(Long childId, Long memberId, LocalDateTime occurredAt) {
        return create(new Values(childId, memberId, null, null, null, null, true, occurredAt));
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓴다.
     *
     * @return 반영했으면 true
     */
    public boolean applyIfNewer(Values values) {
        if (!storedTime(values.occurredAt()).isAfter(eventOccurredAt)) {
            return false;
        }
        apply(values);
        return true;
    }

    /**
     * 키 · 몸무게 없이 온 삭제 이벤트를 반영한다. 다른 값은 그대로 두고 삭제 표시만, 늦게 발생한 이벤트일 때만.
     *
     * @return 반영했으면 true
     */
    public boolean markDeletedIfNewer(LocalDateTime occurredAt) {
        if (!storedTime(occurredAt).isAfter(eventOccurredAt)) {
            return false;
        }
        deleted = true;
        eventOccurredAt = storedTime(occurredAt);
        return true;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    private static LocalDateTime storedTime(LocalDateTime at) {
        return at.truncatedTo(ChronoUnit.MICROS);
    }

    private void apply(Values values) {
        memberId = values.memberId();
        name = values.name();
        heightCm = values.heightCm();
        weightKg = values.weightKg();
        bodyType = values.bodyType();
        deleted = values.deleted();
        eventOccurredAt = storedTime(values.occurredAt());
    }
}
