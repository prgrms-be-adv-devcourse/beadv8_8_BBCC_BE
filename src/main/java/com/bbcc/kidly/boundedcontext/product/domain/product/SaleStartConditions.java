package com.bbcc.kidly.boundedcontext.product.domain.product;

import java.util.ArrayList;
import java.util.List;

/**
 * 판매 시작 조건(SALE-03) 중 채우지 못한 항목을 문장 목록으로 계산한다. Product.startSale이 쓴다.
 */
final class SaleStartConditions {

    private SaleStartConditions() {
    }

    // 빠진 항목마다 마침표로 끝나는 문장을 하나씩 돌려준다. 모두 채웠으면 빈 목록이다
    static List<String> missingItems(List<ProductOption> options) {
        List<String> missing = new ArrayList<>();
        if (ProductOptions.activeValueLists(options).isEmpty()) {
            missing.add("옵션이 하나 이상 있어야 합니다.");
        }
        return missing;
    }
}
