package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 판매대기 상품을 이어 쓸 때 미리 만든 옵션을 지금 옵션과 비교하고, 지금 옵션을 그 값으로 바꿔 넣는다.
 */
final class ProductOptionRevision {

    private ProductOptionRevision() {
    }

    // 미리 만든 옵션이 지금 옵션(보관 제외)과 다른지 본다. 값 조합이 같은 옵션은 SKU 코드와 판매가를 비교하고,
    // 값 조합이 짝지어지지 않으면 다른 것으로 본다
    static boolean differs(List<ProductOption> current, List<ProductOption> previews) {
        Map<Set<AttributeValue>, ProductOption> bySet = activeBySet(current);
        if (bySet.size() != previews.size()) {
            return true;
        }
        for (ProductOption preview : previews) {
            ProductOption matched = bySet.get(valueSet(preview));
            if (matched == null
                || matched.getPrice() != preview.getPrice()
                || !Objects.equals(matched.getSkuCode(), preview.getSkuCode())) {
                return true;
            }
        }
        return false;
    }

    // 값 조합이 같은 옵션은 객체를 그대로 두고 SKU 코드와 판매가만 맞춘다. 미리 만든 옵션에 없는 옵션(보관 제외)은 빼고,
    // 지금 없는 값 조합은 미리 만든 객체를 그대로 넣는다.
    // 판매대기 상품은 주문이 없어서 지워도 이 옵션을 가리키는 주문상품이 없다 (OPT-05)
    static void rewrite(List<ProductOption> current, List<ProductOption> previews) {
        Map<Set<AttributeValue>, ProductOption> bySet = activeBySet(current);
        List<ProductOption> created = new ArrayList<>();
        for (ProductOption preview : previews) {
            ProductOption matched = bySet.remove(valueSet(preview));
            if (matched == null) {
                created.add(preview);
                continue;
            }
            matched.revise(preview.getSkuCode(), preview.getPrice());
        }
        current.removeAll(bySet.values());
        current.addAll(created);
    }

    // 보관하지 않은 옵션을 값 조합으로 찾을 수 있게 모은다. 값 묶음은 순서와 상관없는 집합으로 비교한다
    private static Map<Set<AttributeValue>, ProductOption> activeBySet(List<ProductOption> options) {
        Map<Set<AttributeValue>, ProductOption> bySet = new LinkedHashMap<>();
        for (ProductOption option : ProductOptions.activeOptions(options)) {
            bySet.put(valueSet(option), option);
        }
        return bySet;
    }

    private static Set<AttributeValue> valueSet(ProductOption option) {
        return new HashSet<>(ProductOptions.valuesOf(option));
    }
}
