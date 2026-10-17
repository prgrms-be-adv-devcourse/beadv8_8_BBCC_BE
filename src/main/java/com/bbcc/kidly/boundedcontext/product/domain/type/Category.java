package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
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
 * 상품을 분류하는 카테고리다. 카테고리는 계층을 이루며, 말단 카테고리만 상품 유형과 필수 실측 항목을 가지고
 * 중간 카테고리는 둘 다 없다.
 */
@Entity
@Table(
    schema = "product",
    name = "category",
    uniqueConstraints = @UniqueConstraint(columnNames = {"parent_id", "name"}))
// 최상위 카테고리(parent_id가 null)끼리의 이름 중복은 이 제약이 막지 못한다. UNIQUE는 null끼리 같은 값으로 보지 않는다
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

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CategoryMeasurementItem> measurementItems = new ArrayList<>();

    public static Category createMiddle(String name, Category parent) {
        requireNonNull(name, "name은 필수입니다");
        rejectBlankName(name);
        rejectLeafParent(parent);
        Category category = new Category();
        category.name = name;
        category.parent = parent;
        return category;
    }

    public static Category createLeaf(LeafInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.name(), "name은 필수입니다");
        requireNonNull(info.productType(), "productType은 필수입니다");
        requireNonNull(info.measurementItems(), "measurementItems는 필수입니다");
        info.measurementItems().forEach(item -> requireNonNull(item, "measurementItem은 필수입니다"));
        rejectBlankName(info.name());
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

    // 상품 유형과 필수 실측 항목을 받은 값으로 바꾼다. 상품이 있으면 기존 상품을 깨지 않는 변경만 받는다. 실측 연결 행은 지우고 다시 넣지 않고 그 자리에서 고친다
    public void change(ProductType productType, List<MeasurementItem> measurementItems, boolean hasProducts) {
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(measurementItems, "measurementItems는 필수입니다");
        for (MeasurementItem item : measurementItems) {
            requireNonNull(item, "measurementItem은 필수입니다");
        }
        // 중간 카테고리는 상품 유형과 필수 실측 항목을 가지지 않으므로 바꿀 것이 없다 (TYPE-01)
        if (!isLeaf()) {
            throw new BusinessException(CategoryErrorCode.MIDDLE_CATEGORY_NOT_CHANGEABLE);
        }
        if (hasProducts && breaksProducts(productType, measurementItems)) {
            throw new BusinessException(CategoryErrorCode.CHANGE_BREAKS_PRODUCTS);
        }
        List<CategoryMeasurementItem> changed = new ArrayList<>();
        for (int i = 0; i < measurementItems.size(); i++) {
            changed.add(changeOrCreateMeasurementItem(measurementItems.get(i), i + 1));
        }
        this.productType = productType;
        this.measurementItems.clear();
        this.measurementItems.addAll(changed);
    }

    // 상품이 있는 카테고리는 상품 유형을 그대로 두고 필수 실측 항목을 빼거나 순서만 바꿀 수 있다 (TYPE-07)
    private boolean breaksProducts(ProductType newType, List<MeasurementItem> newItems) {
        if (newType != productType) {
            return true;
        }
        for (MeasurementItem item : newItems) {
            if (!hasMeasurementItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMeasurementItem(MeasurementItem item) {
        for (CategoryMeasurementItem link : measurementItems) {
            if (link.getMeasurementItem() == item) {
                return true;
            }
        }
        return false;
    }

    private CategoryMeasurementItem changeOrCreateMeasurementItem(MeasurementItem item, int sortOrder) {
        for (CategoryMeasurementItem link : measurementItems) {
            if (link.getMeasurementItem() == item) {
                link.changeSortOrder(sortOrder);
                return link;
            }
        }
        return CategoryMeasurementItem.create(this, item, sortOrder);
    }

    private static void rejectBlankName(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("카테고리 이름은 비어 있을 수 없습니다");
        }
    }

    // 유형을 가진 카테고리 아래에 카테고리를 만들면, 그 부모가 유형을 가진 중간 카테고리가 되어 TYPE-01을 어긴다.
    // 부모가 지연 로딩 프록시이면 필드가 비어 있으므로 getter로 읽는다
    private static void rejectLeafParent(Category parent) {
        if (parent != null && parent.getProductType() != null) {
            throw new BusinessException(CategoryErrorCode.PARENT_IS_LEAF);
        }
    }

    // 말단 카테고리인지 본다. 말단 카테고리만 상품 유형을 가진다 (TYPE-01)
    public boolean isLeaf() {
        return getProductType() != null;
    }

    // 상품을 등록할 때 쓸 상품 유형이다. 중간 카테고리는 유형이 없어 거절한다 (TYPE-01)
    public ProductType productTypeToRegister() {
        if (productType == null) {
            throw new BusinessException(CategoryErrorCode.NOT_LEAF_CATEGORY);
        }
        return productType;
    }

    public List<CategoryMeasurementItem> getMeasurementItems() {
        return Collections.unmodifiableList(measurementItems);
    }
}
