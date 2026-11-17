package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNullElse;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "product", name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseSoftDeleteEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryVisibleGender visibleGender = CategoryVisibleGender.ALL;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CategoryMeasurementItem> measurementItems = new ArrayList<>();

    public static Category createMiddle(String name, Category parent) {
        rejectLeafParent(parent);
        rejectDeletedReferences(parent, null, List.of());
        var category = new Category();
        category.name = name;
        category.parent = parent;
        return category;
    }

    public static Category createLeaf(LeafInfo info) {
        rejectDuplicatedMeasurementItems(info.measurementItems());
        rejectLeafParent(info.parent());
        rejectDeletedReferences(info.parent(), info.productType(), info.measurementItems());
        var category = new Category();
        category.name = info.name();
        category.parent = info.parent();
        category.productType = info.productType();
        for (int i = 0; i < info.measurementItems().size(); i++) {
            category.measurementItems.add(
                CategoryMeasurementItem.create(category, info.measurementItems().get(i), i + 1));
        }
        return category;
    }

    public void changeVisibleGender(CategoryVisibleGender visibleGender) {
        this.visibleGender = requireNonNullElse(visibleGender, CategoryVisibleGender.ALL);
    }

    public boolean isVisibleTo(Gender childGender) {
        return !(childGender == Gender.MALE && visibleGender == CategoryVisibleGender.GIRL);
    }

    public void change(ProductType productType, List<MeasurementItem> measurementItems, boolean hasProducts) {
        rejectDuplicatedMeasurementItems(measurementItems);
        // 중간 카테고리는 상품 유형과 필수 실측 항목을 가지지 않으므로 바꿀 것이 없다 (TYPE-01)
        if (!isLeaf()) {
            throw new BusinessException(CategoryErrorCode.MIDDLE_CATEGORY_NOT_CHANGEABLE);
        }
        rejectDeletedReferences(null, productType, measurementItems);
        if (hasProducts && breaksProducts(productType, measurementItems)) {
            throw new BusinessException(CategoryErrorCode.CHANGE_BREAKS_PRODUCTS);
        }
        var changed = new ArrayList<CategoryMeasurementItem>();
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
        return newItems.stream().anyMatch(item -> findLink(item) == null);
    }

    private CategoryMeasurementItem findLink(MeasurementItem item) {
        for (CategoryMeasurementItem link : measurementItems) {
            if (link.getMeasurementItem() == item) {
                return link;
            }
        }
        return null;
    }

    private CategoryMeasurementItem changeOrCreateMeasurementItem(MeasurementItem item, int sortOrder) {
        CategoryMeasurementItem link = findLink(item);
        if (link == null) {
            return CategoryMeasurementItem.create(this, item, sortOrder);
        }
        link.changeSortOrder(sortOrder);
        return link;
    }

    private static void rejectDuplicatedMeasurementItems(List<MeasurementItem> items) {
        if (new HashSet<>(items).size() != items.size()) {
            throw new BusinessException(CategoryErrorCode.DUPLICATE_MEASUREMENT_ITEM);
        }
    }

    // 지운 부모 카테고리 · 상품 유형 · 실측 항목은 새로 고를 수 없다(TYPE-10: 지운 기준 데이터는 새로 연결하지 않는다).
    // 부모와 유형은 지연 로딩 프록시일 수 있으므로 메서드로 읽는다
    private static void rejectDeletedReferences(
        Category parent, ProductType productType, List<MeasurementItem> measurementItems) {
        if (parent != null && parent.isDeleted()) {
            throw new BusinessException(CategoryErrorCode.DELETED_REFERENCE);
        }
        if (productType != null && productType.isDeleted()) {
            throw new BusinessException(CategoryErrorCode.DELETED_REFERENCE);
        }
        for (MeasurementItem item : measurementItems) {
            if (item.isDeleted()) {
                throw new BusinessException(CategoryErrorCode.DELETED_REFERENCE);
            }
        }
    }

    // 유형을 가진 카테고리 아래에 카테고리를 만들면, 그 부모가 유형을 가진 중간 카테고리가 되어 TYPE-01을 어긴다.
    // 부모가 지연 로딩 프록시이면 필드가 비어 있으므로 getter로 읽는다
    private static void rejectLeafParent(Category parent) {
        if (parent != null && parent.getProductType() != null) {
            throw new BusinessException(CategoryErrorCode.PARENT_IS_LEAF);
        }
    }

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

    // 부모는 지연 로딩 프록시일 수 있으므로 필드가 아니라 getter로 따라간다
    public List<Category> path() {
        var path = new ArrayList<Category>();
        for (Category current = this; current != null; current = current.getParent()) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    public List<CategoryMeasurementItem> getMeasurementItems() {
        return Collections.unmodifiableList(measurementItems);
    }

    // 지우지 않은 하위 카테고리가 있는 카테고리(inUse)는 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다)
    public void delete(LocalDateTime now, boolean inUse) {
        if (inUse) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_IN_USE);
        }
        delete(now);
    }
}
