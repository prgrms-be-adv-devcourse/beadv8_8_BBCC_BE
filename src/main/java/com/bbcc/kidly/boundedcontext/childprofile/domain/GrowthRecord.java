package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀의 키 · 체형 변화 기록. 등록할 때와 키 · 체형이 바뀔 때 {@link ChildProfile}이 1건씩 쌓는다. 수정 · 삭제하지 않는다.
 * 몸무게는 기록하지 않는다. 쌓이는 기준이 키 · 체형이기 때문이다.
 */
@Entity
@Table(
    name = "growth_record",
    schema = "childprofile",
    indexes = @Index(name = "idx_growth_record_child_profile_id", columnList = "child_profile_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrowthRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_profile_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_growth_record_child_profile"))
    private ChildProfile childProfile;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    GrowthRecord(ChildProfile childProfile, BigDecimal heightCm, BodyType bodyType, LocalDateTime recordedAt) {
        this.childProfile = childProfile;
        this.heightCm = heightCm;
        this.bodyType = bodyType;
        this.recordedAt = recordedAt;
    }
}
