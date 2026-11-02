package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 정보 변경됨 이벤트의 사이즈별 실측 한 줄이다. 상품 도메인의 사이즈 스펙과 이름이 겹치지 않게 앞에 복사본 이름을 붙인다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaSizeSpec {

    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "min_height_cm")
    private Integer minHeightCm;

    @Column(name = "max_height_cm")
    private Integer maxHeightCm;

    // 키는 실측 항목 코드, 값은 cm다. 읽을 때는 getMeasurements로 읽기 전용 Map을 돌려준다
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, BigDecimal> measurements;

    public RecommendReplicaSizeSpec(String size, Integer minHeightCm, Integer maxHeightCm,
        Map<String, BigDecimal> measurements) {
        this.size = size;
        this.minHeightCm = minHeightCm;
        this.maxHeightCm = maxHeightCm;
        this.measurements = Map.copyOf(measurements);
    }

    public Map<String, BigDecimal> getMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }
}
