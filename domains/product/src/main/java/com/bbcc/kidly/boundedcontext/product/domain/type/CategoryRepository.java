package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    Optional<Category> findTopLevelByName(String name);

    List<Category> findAllByDeletedAtIsNullOrderByIdAsc();

    default Category getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    Optional<Category> findByIdForUpdate(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Category getByIdForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    Optional<Category> findByIdForShare(Long id);

    // 같은 트랜잭션에서 이 엔티티를 이미 읽었으면 잠그기만 하고 값은 다시 읽지 않으므로, 트랜잭션에서 처음 읽을 때 쓴다.
    default Category getByIdForShare(Long id) {
        return findByIdForShare(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    boolean existsByProductTypeAndDeletedAtIsNull(ProductType productType);

    boolean existsByParentAndDeletedAtIsNull(Category parent);

    boolean existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(MeasurementItem measurementItem);
}
