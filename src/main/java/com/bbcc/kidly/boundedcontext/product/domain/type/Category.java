package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 트리의 한 칸이다. 말단 카테고리만 상품 유형과 필수 실측 항목을 가지고, 중간 카테고리는 둘 다 없다.
 */
@Entity
@Table(
    schema = "product",
    name = "category",
    uniqueConstraints = @UniqueConstraint(columnNames = {"parent_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    /**
     * 말단 카테고리를 만드는 값이다. 최상위 카테고리이면 parent는 null이다.
     */
    public record LeafInfo(
        String name,
        Category parent,
        ProductType productType,
        List<MeasurementItem> measurementItems) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    @OrderBy("sortOrder ASC")
    private List<CategoryMeasurementItem> measurementItems = new ArrayList<>();

    public static Category createMiddle(String name, Category parent) {
        rejectLeafParent(parent);
        Category category = new Category();
        category.name = name;
        category.parent = parent;
        return category;
    }

    public static Category createLeaf(LeafInfo info) {
        rejectLeafParent(info.parent());
        Category category = new Category();
        category.name = info.name();
        category.parent = info.parent();
        category.productType = info.productType();
        for (int i = 0; i < info.measurementItems().size(); i++) {
            category.measurementItems.add(
                CategoryMeasurementItem.create(category, info.measurementItems().get(i), i + 1));
        }
        return category;
    }

    // 말단 카테고리는 상품 유형을 가리키므로 그 아래에 카테고리를 두면 상품이 어느 유형을 쓸지 정할 수 없다 (TYPE-01)
    private static void rejectLeafParent(Category parent) {
        if (parent != null && parent.productType != null) {
            throw new IllegalArgumentException("말단 카테고리 아래에는 카테고리를 만들 수 없습니다");
        }
    }

    // 상품을 등록할 때 쓸 상품 유형이다
    public ProductType productTypeToRegister() {
        return productType;
    }

    public List<CategoryMeasurementItem> getMeasurementItems() {
        return Collections.unmodifiableList(measurementItems);
    }
}
