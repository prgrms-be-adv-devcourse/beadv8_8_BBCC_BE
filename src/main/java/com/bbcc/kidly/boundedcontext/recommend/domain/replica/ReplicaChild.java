package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천이 사이즈를 계산하려고 자녀 정보 변경됨 이벤트에서 옮겨 둔 자녀 정보다.
 * 삭제된 자녀도 조회되어야 늦게 도착한 이전 이벤트로 복사본이 다시 생기지 않으므로 {@code @SQLRestriction}을 붙이지 않는다.
 */
@Entity
@Table(schema = "recommend", name = "replica_child")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaChild extends BaseSoftDeleteEntity {

    // 원래 자녀 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "child_profile_id")
    private Long childProfileId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_fit")
    private PreferredFit preferredFit;

    @Column(name = "event_occurred_at", nullable = false)
    private LocalDateTime eventOccurredAt;

    public record ChangeInfo(Long childProfileId, Long memberId, BigDecimal heightCm, BigDecimal weightKg,
        Gender gender, LocalDate birthDate, BodyType bodyType, PreferredFit preferredFit, boolean deleted,
        LocalDateTime occurredAt) {
    }

    public static ReplicaChild create(ChangeInfo info) {
        ReplicaChild child = new ReplicaChild();
        child.childProfileId = info.childProfileId();
        child.memberId = info.memberId();
        child.heightCm = info.heightCm();
        child.weightKg = info.weightKg();
        child.gender = info.gender();
        child.birthDate = info.birthDate();
        child.bodyType = info.bodyType();
        child.preferredFit = info.preferredFit();
        child.eventOccurredAt = info.occurredAt();
        return child;
    }
}
