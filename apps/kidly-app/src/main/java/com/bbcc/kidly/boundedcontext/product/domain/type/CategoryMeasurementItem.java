package com.bbcc.kidly.boundedcontext.product.domain.type;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 말단 카테고리가 필수로 받는 실측 항목 하나와 그 순서다.
 */
@Entity
@Table(
    schema = "product",
    name = "category_measurement_item",
    uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "measurement_item_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryMeasurementItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "measurement_item_id")
    private MeasurementItem measurementItem;

    @Column(nullable = false)
    private int sortOrder;

    static CategoryMeasurementItem create(Category category, MeasurementItem measurementItem, int sortOrder) {
        CategoryMeasurementItem link = new CategoryMeasurementItem();
        link.category = category;
        link.measurementItem = measurementItem;
        link.sortOrder = sortOrder;
        return link;
    }

    // 같은 실측 항목의 연결을 그 자리에서 고친다. 연결 행을 지우고 다시 넣으면 UNIQUE 제약에 걸린다
    void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
