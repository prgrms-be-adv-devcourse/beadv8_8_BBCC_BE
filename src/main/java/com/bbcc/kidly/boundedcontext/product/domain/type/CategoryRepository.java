package com.bbcc.kidly.boundedcontext.product.domain.type;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    // 카테고리를 저장한다. 말단 카테고리이면 필수 실측 항목도 함께 저장한다
    Category save(Category category);

    // 카테고리를 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Category> findById(Long id);

    // 부모가 없는 최상위 카테고리를 이름으로 찾는다. 없으면 빈 값을 돌려준다
    // UNIQUE(parent_id, name)는 parent_id가 null인 행끼리의 이름 중복을 막지 못하므로, 관리자가 최상위 카테고리를 만들기 전에 이것으로 이미 있는지 확인한다
    Optional<Category> findTopLevelByName(String name);

    // 모든 카테고리를 ID 순서로 돌려준다
    List<Category> findAll();
}
