package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 총장 · 어깨너비처럼 상품을 재는 실측 항목이다. 말단 카테고리가 어떤 실측 항목을 필수로 받는지 정할 때 쓴다.
 */
@Entity
@Table(schema = "product", name = "measurement_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    public static MeasurementItem create(String code, String name, String unit) {
        MeasurementItem item = new MeasurementItem();
        item.code = code;
        item.name = name;
        item.unit = unit;
        return item;
    }
}
