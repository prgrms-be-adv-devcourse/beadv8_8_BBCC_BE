package com.bbcc.kidly.boundedcontext.order.domain.replica;

import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 계정 → 자녀 프로필 · 보호자 복사본(#906, child-account.changed). 조르기가 자녀 로그인을 자기 프로필로 바꿀 때 읽는다.
 * 계정 ID는 다시 쓰이지 않으므로 삭제는 한 방향(있음 → 삭제)이다: 삭제 메시지는 시각과 상관없이 반영하고, 삭제 뒤 들어온 예전 생성 메시지는
 * 무시한다. 삭제되지 않은 계정은 저장된 시각보다 늦은 메시지만 반영한다. 그래서 같은 메시지를 다시 받거나 순서가 바뀌어도 결과가 같다.
 * 시각은 DB 정밀도(마이크로초)로 잘라 저장 · 비교한다(자녀 복사본과 같음).
 */
@Entity
@Table(schema = "orders", name = "replica_child_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaChildAccount extends OrderTimeEntity {

    /**
     * 복사본 입력 값.
     *
     * @param deleted 계정이 지워졌으면 true
     * @param occurredAt 이벤트 발생 시각
     */
    public record Values(
        Long accountId,
        Long childProfileId,
        Long guardianId,
        boolean deleted,
        LocalDateTime occurredAt) {
    }

    // 자녀 계정 ID를 그대로 키로 쓴다
    @Id
    private Long accountId;

    @Column(nullable = false)
    private Long childProfileId;

    @Column(nullable = false)
    private Long guardianId;

    /**
     * 삭제를 반영한 메시지의 발생 시각. 삭제되지 않았으면 null.
     */
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime eventOccurredAt;

    public static ReplicaChildAccount create(Values values) {
        ReplicaChildAccount replica = new ReplicaChildAccount();
        replica.accountId = values.accountId();
        replica.apply(values);
        return replica;
    }

    /**
     * 삭제는 한 번만(이미 삭제면 그대로), 생성 · 변경은 삭제되지 않았고 더 늦은 메시지일 때만 반영한다.
     *
     * @return 반영했으면 true
     */
    public boolean applyIfNewer(Values values) {
        if (isDeleted()) {
            return false;
        }
        if (!values.deleted() && !micros(values.occurredAt()).isAfter(eventOccurredAt)) {
            return false;
        }
        apply(values);
        return true;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void apply(Values values) {
        childProfileId = values.childProfileId();
        guardianId = values.guardianId();
        eventOccurredAt = micros(values.occurredAt());
        if (values.deleted()) {
            deletedAt = eventOccurredAt;
        }
    }

    private static LocalDateTime micros(LocalDateTime at) {
        return at.truncatedTo(ChronoUnit.MICROS);
    }
}
