package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// SKU 코드는 같은 판매자의 다른 옵션과 겹치지 않는다 (OPT-07)
public final class SkuCodes {

    private SkuCodes() {
    }

    // 등록하려는 SKU 코드가 같은 판매자의 옵션이 이미 쓰는 코드와 겹치면 409로 거절하고, 겹친 코드를 요청 순서대로 알려 준다.
    public static void validateUnique(List<String> newCodes, Collection<String> codesInUse) {
        List<String> duplicated = new ArrayList<>();
        for (String code : newCodes) {
            if (codesInUse.contains(code)) {
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
