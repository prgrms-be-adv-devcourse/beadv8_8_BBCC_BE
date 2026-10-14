package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 최상위 카테고리부터 하위 카테고리까지 계층으로 돌려준다. 같은 부모 아래에서는 ID 순서다
    @Transactional(readOnly = true)
    public List<CategoryTreeResult> findTree() {
        List<Category> topLevel = new ArrayList<>();
        Map<Long, List<Category>> childrenByParentId = new HashMap<>();
        for (Category category : categoryRepository.findAll()) {
            // 부모는 지연 로딩 프록시이므로 ID만 읽는다. 프록시를 초기화하지 않아 쿼리가 늘지 않는다
            Category parent = category.getParent();
            if (parent == null) {
                topLevel.add(category);
                continue;
            }
            childrenByParentId.computeIfAbsent(parent.getId(), id -> new ArrayList<>()).add(category);
        }
        return toResults(topLevel, childrenByParentId);
    }

    private List<CategoryTreeResult> toResults(
        List<Category> categories, Map<Long, List<Category>> childrenByParentId) {
        List<CategoryTreeResult> results = new ArrayList<>();
        for (Category category : categories) {
            List<Category> children = childrenByParentId.getOrDefault(category.getId(), List.of());
            results.add(new CategoryTreeResult(
                category.getId(),
                category.getName(),
                category.getProductType() != null,
                toResults(children, childrenByParentId)));
        }
        return results;
    }
}
