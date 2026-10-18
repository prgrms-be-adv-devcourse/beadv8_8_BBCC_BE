package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    // 카테고리를 저장한다. 말단 카테고리이면 필수 실측 항목도 함께 저장한다
    Category save(Category category);

    // 카테고리를 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Category> findById(Long id);

    // 부모가 없는 최상위 카테고리 중 지우지 않은 것을 이름으로 찾는다. 없으면 빈 값을 돌려준다
    // UNIQUE(parent_id, name)는 parent_id가 null인 행끼리의 이름 중복을 막지 못하므로,
    // 관리자가 최상위 카테고리를 만들기 전에 이것으로 이미 있는지 확인한다
    // (TYPE-11: 지운 카테고리와 같은 이름은 만들 수 있다)
    Optional<Category> findTopLevelByName(String name);

    // 지우지 않은 카테고리만 ID 순서로 돌려준다. 선택지 조회에 쓴다 (TYPE-09)
    List<Category> findAllByDeletedAtIsNullOrderByIdAsc();

    // 카테고리를 ID로 찾는다. 없으면 404 오류(CATEGORY_NOT_FOUND)를 던진다
    default Category getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    // 카테고리를 행 잠금(FOR UPDATE)으로 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Category> findByIdForUpdate(Long id);

    // 카테고리를 행 잠금(FOR UPDATE)으로 ID로 찾는다. 없으면 404 오류(CATEGORY_NOT_FOUND)를 던진다
    default Category getByIdForUpdate(Long id) {
        return findByIdForUpdate(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    // 카테고리를 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Category> findByIdForShare(Long id);

    // 카테고리를 공유 잠금(FOR SHARE)으로 ID로 찾는다. 없으면 404 오류(CATEGORY_NOT_FOUND)를 던진다
    default Category getByIdForShare(Long id) {
        return findByIdForShare(id).orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }

    // 아래 셋은 지우지 않은 카테고리만 본다. 지운 카테고리가 가리키는 유형 · 부모 · 실측 항목은 지울 수 있다 (TYPE-10)

    // 그 유형을 가리키는 카테고리가 있는지 본다
    boolean existsByProductTypeAndDeletedAtIsNull(ProductType productType);

    // 그 카테고리를 부모로 둔 카테고리가 있는지 본다
    boolean existsByParentAndDeletedAtIsNull(Category parent);

    // 그 실측 항목을 필수로 연결한 카테고리가 있는지 본다
    boolean existsByMeasurementItemsMeasurementItemAndDeletedAtIsNull(MeasurementItem measurementItem);
}
