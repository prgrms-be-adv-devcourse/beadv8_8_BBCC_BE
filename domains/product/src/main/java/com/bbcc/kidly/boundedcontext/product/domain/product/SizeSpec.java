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

    private Integer minHeightCm;

    private Integer maxHeightCm;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, BigDecimal> measurements;

    static SizeSpec create(Product product, Product.SizeSpecInfo info) {
        var sizeSpec = new SizeSpec();
        sizeSpec.product = product;
        sizeSpec.sizeValue = info.sizeValue();
        sizeSpec.minHeightCm = info.minHeightCm();
        sizeSpec.maxHeightCm = info.maxHeightCm();
        sizeSpec.measurements = Map.copyOf(info.measurements());
        return sizeSpec;
    }

    void replaceValues(Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
        this.minHeightCm = minHeightCm;
        this.maxHeightCm = maxHeightCm;
        this.measurements = Map.copyOf(measurements);
    }

    public Map<String, BigDecimal> getMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }
}
