package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 성장 기록 이벤트를 받아 남기는 성장 기록 복사본이다.
 * 다른 도메인 데이터의 복사본이므로 원래 기록 ID를 PK로 쓰고, 자녀 ID는 FK 없이 값으로만 참조한다.
 */
@Entity
@Table(schema = "recommend", name = "growth_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendGrowthRecord extends BaseTimeEntity {

    @Id
    @Column(name = "growth_record_id")
    private Long growthRecordId;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    // 체형을 입력하지 않은 기록은 null이다
    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public record RecordInfo(Long growthRecordId, Long childProfileId, BigDecimal heightCm, BodyType bodyType,
        LocalDateTime recordedAt) {
    }

    public static RecommendGrowthRecord create(RecordInfo info) {
        requireNonNull(info, "info는 필수입니다");
        RecommendGrowthRecord record = new RecommendGrowthRecord();
        record.growthRecordId = requireNonNull(info.growthRecordId(), "growthRecordId는 필수입니다");
        record.childProfileId = requireNonNull(info.childProfileId(), "childProfileId는 필수입니다");
        record.heightCm = requireNonNull(info.heightCm(), "heightCm는 필수입니다");
        record.bodyType = info.bodyType();
        record.recordedAt = requireNonNull(info.recordedAt(), "recordedAt은 필수입니다");
        return record;
    }
}
