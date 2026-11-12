package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 자녀가 지금 잘 맞게 입는 옷 한 벌이다. 자녀마다 구분(상의 · 바지 · 아우터 · 원피스 · 치마)별로 한 줄만 있고, 다시 저장하면 그 줄의 값을 바꾼다.
 * 자녀와 세부 종류 카테고리는 다른 도메인의 ID라 FK 없이 값으로만 참조한다.
 */
@Entity
@Table(schema = "recommend", name = "reference_garment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceGarment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GarmentPart part;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // 키는 실측 항목 코드, 값은 cm다. DB에서 읽은 Map은 바꿀 수 있어서,
    // 검증 없이 바뀌지 않도록 getMeasurements가 읽기 전용 Map을 돌려준다
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, BigDecimal> measurements;

    public static ReferenceGarment create(Long childProfileId, ReferenceGarmentSpec spec) {
        requireNonNull(spec, "spec은 필수입니다");
        ReferenceGarment garment = new ReferenceGarment();
        garment.childProfileId = requireNonNull(childProfileId, "childProfileId는 필수입니다");
        garment.part = spec.part();
        garment.categoryId = spec.categoryId();
        garment.measurements = spec.measurements();
        return garment;
    }

    /**
     * 같은 구분의 새 값으로 세부 종류와 실측을 바꾼다. 구분이 다른 값은 다른 줄의 것이므로 받지 않는다.
     */
    public void replace(ReferenceGarmentSpec spec) {
        requireNonNull(spec, "spec은 필수입니다");
        if (spec.part() != part) {
            throw new IllegalArgumentException("구분이 다른 값으로는 바꿀 수 없습니다");
        }
        this.categoryId = spec.categoryId();
        this.measurements = spec.measurements();
    }

    public Map<String, BigDecimal> getMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }
}
