package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.in.dto.InternalProductSearchRequest;
import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 사이 전용 상품 검색(#1191, 챗봇). 검색 계약 {@link ProductSearcher}(shared)를 같은 모양({@link ProductHit} 배열)으로
 * HTTP에 낸다. 사용자 인증 헤더를 요구하지 않는다: 밖에서는 Gateway가 /internal/**을 막는다.
 */
@RestController
@RequiredArgsConstructor
public class InternalProductSearchController {

    public static final String PRODUCT_SEARCH_PATH = "/internal/products/search";

    private final ProductSearcher productSearcher;

    /**
     * 200 + 관련도순 판매중 상품(없으면 []). 잘못된 값은 400, 검색 색인을 쓸 수 없으면 503 SEARCH_UNAVAILABLE.
     */
    @GetMapping(PRODUCT_SEARCH_PATH)
    public List<ProductHit> search(@Valid @ModelAttribute InternalProductSearchRequest request) {
        return productSearcher.search(request.toQuery());
    }
}
