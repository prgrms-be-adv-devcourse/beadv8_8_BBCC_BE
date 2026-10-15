package com.bbcc.kidly.boundedcontext.product.app.search;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
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

    public Page<ProductSearchItem> list() {
        ProductSearchCondition condition = new ProductSearchCondition(null, null, Map.of(), ProductSearchSort.NEWEST);
        return productSearch.search(condition, PageRequest.of(0, 20));
    }
}
