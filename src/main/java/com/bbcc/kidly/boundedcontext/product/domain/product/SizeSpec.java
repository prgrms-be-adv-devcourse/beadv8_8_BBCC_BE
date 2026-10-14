package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * 상품의 사이즈 값 하나에 대한 실측과 권장 키 범위다. 상품과 사이즈 값마다 한 행이다.
 * 이 행을 동시에 바꾸는 일은 상품의 version이 막으므로 이 행에는 version을 두지 않는다.
 */
@Entity
@Table(schema = "product", name = "size_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SizeSpec extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_value_id")
    private AttributeValue sizeValue;

    // 권장 키는 판매 시작 전까지 비어 있을 수 있고, 사이즈 무관(FREE) 행은 판매 중에도 없을 수 있어 null을 허용한다
    private Integer minHeightCm;

    private Integer maxHeightCm;

    // 키는 실측 항목 코드이고 값은 cm다
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, BigDecimal> measurements;

    static SizeSpec create(Product product, Product.SizeSpecInfo info) {
        SizeSpec sizeSpec = new SizeSpec();
        sizeSpec.product = product;
        sizeSpec.sizeValue = info.sizeValue();
        sizeSpec.minHeightCm = info.minHeightCm();
        sizeSpec.maxHeightCm = info.maxHeightCm();
        sizeSpec.measurements = Map.copyOf(info.measurements());
        return sizeSpec;
    }

    // DB에서 읽은 맵도 밖에서 바꾸지 못하게 읽기 전용으로 돌려준다
    public Map<String, BigDecimal> getMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }
}
