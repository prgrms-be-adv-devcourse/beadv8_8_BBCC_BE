package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// SKU 코드는 같은 판매자의 다른 옵션과 겹치지 않는다 (OPT-07)
public final class SkuCodes {

    private SkuCodes() {
    }

    // 등록하려는 SKU 코드가 같은 판매자의 옵션이 이미 쓰는 코드와 겹치거나 새 코드끼리 겹치면 409로 거절한다.
    // 겹친 코드는 요청 순서대로 한 번씩 알려 준다.
    public static void validateUnique(List<String> newCodes, Collection<String> codesInUse) {
        Set<String> seen = new HashSet<>(codesInUse);
        Set<String> duplicated = new LinkedHashSet<>();
        for (String code : newCodes) {
            if (!seen.add(code)) {
                duplicated.add(code);
            }
        }
        if (!duplicated.isEmpty()) {
            String detail = ProductErrorCode.DUPLICATE_SKU_CODE.detail()
                + " 겹친 코드는 " + String.join(", ", duplicated) + "입니다.";
            throw new BusinessException(ProductErrorCode.DUPLICATE_SKU_CODE, detail);
        }
    }
}
