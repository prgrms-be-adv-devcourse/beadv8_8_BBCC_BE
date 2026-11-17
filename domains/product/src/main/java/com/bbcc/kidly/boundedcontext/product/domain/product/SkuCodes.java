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

    public static void validateUnique(List<String> newCodes, Collection<String> codesInUse) {
        Set<String> seen = new HashSet<>(codesInUse);
        Set<String> duplicated = new LinkedHashSet<>();
        for (String code : newCodes) {
            if (code != null && !seen.add(code)) {
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
