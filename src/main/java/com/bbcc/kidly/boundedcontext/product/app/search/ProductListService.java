package com.bbcc.kidly.boundedcontext.product.app.search;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import com.bbcc.kidly.boundedcontext.product.app.search.dto.ProductListRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 상품 목록 조회 유스케이스다. 업무 규칙 없이 조회 요청서에 조건을 넘겨 결과를 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductListService {

    private final ProductSearch productSearch;

    public Page<ProductSearchItem> list(ProductListRequest request) {
        ProductSearchCondition condition = new ProductSearchCondition(
            request.categoryId(),
            request.keyword(),
            groupByCode(request.attributesOrEmpty()),
            ProductSearchSort.valueOf(request.sortOrDefault()));
        return productSearch.search(condition, PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
    }

    // "COLOR:3" 형태의 문자열을 항목 code별 값 ID 목록으로 묶는다. 같은 code는 한 목록에 모으고 같은 값은 한 번만 넣는다.
    // 업무 규칙이 아니라 입력 형식 변환이다
    private Map<String, List<Long>> groupByCode(List<String> attributes) {
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (String attribute : attributes) {
            int separator = attribute.indexOf(':');
            String code = attribute.substring(0, separator);
            Long valueId = Long.valueOf(attribute.substring(separator + 1));
            List<Long> valueIds = grouped.computeIfAbsent(code, key -> new ArrayList<>());
            if (!valueIds.contains(valueId)) {
                valueIds.add(valueId);
            }
        }
        return grouped;
    }
}
