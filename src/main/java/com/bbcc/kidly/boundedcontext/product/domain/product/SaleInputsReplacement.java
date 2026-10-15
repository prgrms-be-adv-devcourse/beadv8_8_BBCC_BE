package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddOptionsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddSizeSpecsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SaleInputsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.UpdateResult;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 판매대기 상품의 옵션 · 가격 · 실측을 받은 값으로 바꿔 넣는다. 받은 값을 검사해 상품에 붙이지 않은 옵션 · 실측을
 * 미리 만들어 두고, 지금 값과 비교하고, 지금 옵션 · 실측을 그 값으로 바꿔 넣는다.
 */
final class SaleInputsReplacement {

    private final List<ProductOption> options;
    private final List<SizeSpec> sizeSpecs;

    private SaleInputsReplacement(List<ProductOption> options, List<SizeSpec> sizeSpecs) {
        this.options = options;
        this.sizeSpecs = sizeSpecs;
    }

    // 받은 옵션 · 가격 · 실측을 등록과 같은 규칙으로 검사한다. 지금 옵션 · 실측은 모두 갈아끼우므로 없다고 보고,
    // 바꿔 넣은 뒤의 모습은 상품에 붙이지 않는 옵션 · 실측으로 미리 만들어 검사한다. 거절되면 아무것도 바뀌지 않는다
    static SaleInputsReplacement prepare(Product product, SaleInputsInfo saleInputs, Category newCategory) {
        ProductType productType = newCategory.productTypeToRegister();
        Long sellerId = product.getSellerId();
        AddOptionsInfo addOptions = new AddOptionsInfo(sellerId, saleInputs.options(), saleInputs.prices());
        ProductOptions.requireNonNullValues(addOptions);
        ProductSizeSpecs.requireNonNullValues(new AddSizeSpecsInfo(sellerId, saleInputs.sizeSpecs()));
        // 등록처럼 옵션 · 실측 목록이 비면 그 목록의 검사는 건너뛰고, 빠진 입력은 아래 판매 시작 입력 검사가 400으로 알린다
        if (!saleInputs.options().isEmpty()) {
            ProductOptions.validateNewOptions(addOptions, List.of(), productType);
        }
        List<ProductOption> options = new ArrayList<>();
        for (OptionInfo option : saleInputs.options()) {
            options.add(ProductOptions.newOption(product, option, saleInputs.prices(), productType));
        }
        if (!saleInputs.sizeSpecs().isEmpty()) {
            ProductSizeSpecs.validateNewSpecs(saleInputs.sizeSpecs(), List.of(), options, newCategory, productType);
        }
        List<SizeSpec> sizeSpecs = new ArrayList<>();
        for (SizeSpecInfo spec : saleInputs.sizeSpecs()) {
            sizeSpecs.add(SizeSpec.create(product, spec));
        }
        SaleStartConditions.requireComplete(
            options, sizeSpecs, newCategory, productType, ProductErrorCode.SALE_START_INPUT_MISSING);
        return new SaleInputsReplacement(options, sizeSpecs);
    }

    // 미리 만든 옵션 · 실측이 지금 값과 다른지 본다
    boolean differsFrom(List<ProductOption> currentOptions, List<SizeSpec> currentSizeSpecs) {
        return differsOptions(currentOptions) || differsSizeSpecs(currentSizeSpecs);
    }

    // 지금 옵션 · 실측을 미리 만든 값으로 바꿔 넣고, 뺀 옵션과 받은 순서대로 짝지은 옵션을 알려 준다
    UpdateResult replace(List<ProductOption> currentOptions, List<SizeSpec> currentSizeSpecs) {
        UpdateResult result = replaceOptions(currentOptions);
        replaceSizeSpecs(currentSizeSpecs);
        return result;
    }

    // 지금 옵션(보관 제외)과 다른지 본다. 값 조합이 같은 옵션은 SKU 코드와 판매가를 비교하고,
    // 값 조합이 짝지어지지 않으면 다른 것으로 본다
    private boolean differsOptions(List<ProductOption> current) {
        Map<Set<AttributeValue>, ProductOption> bySet = activeBySet(current);
        if (bySet.size() != options.size()) {
            return true;
        }
        for (ProductOption prepared : options) {
            ProductOption matched = bySet.get(valueSet(prepared));
            if (matched == null
                || matched.getPrice() != prepared.getPrice()
                || !Objects.equals(matched.getSkuCode(), prepared.getSkuCode())) {
                return true;
            }
        }
        return false;
    }

    // 지금 실측과 다른지 본다. 사이즈 값이 짝지어지지 않으면 다른 것으로 본다
    private boolean differsSizeSpecs(List<SizeSpec> current) {
        Map<AttributeValue, SizeSpec> bySize = bySize(current);
        if (current.size() != sizeSpecs.size()) {
            return true;
        }
        for (SizeSpec prepared : sizeSpecs) {
            SizeSpec matched = bySize.get(prepared.getSizeValue());
            if (matched == null
                || !Objects.equals(matched.getMinHeightCm(), prepared.getMinHeightCm())
                || !Objects.equals(matched.getMaxHeightCm(), prepared.getMaxHeightCm())
                || !sameMeasurements(matched.getMeasurements(), prepared.getMeasurements())) {
                return true;
            }
        }
        return false;
    }

    // 값 조합이 같은 옵션은 객체를 그대로 두고 SKU 코드와 판매가만 맞춘다. 미리 만든 옵션에 없는 옵션(보관 제외)은 빼고,
    // 지금 없는 값 조합은 미리 만든 객체를 그대로 넣는다. 뺀 옵션과 받은 순서대로 짝지은 옵션을 돌려준다.
    // 판매대기 상품은 주문이 없어서 지워도 이 옵션을 가리키는 주문상품이 없다 (OPT-05)
    private UpdateResult replaceOptions(List<ProductOption> current) {
        Map<Set<AttributeValue>, ProductOption> bySet = activeBySet(current);
        List<ProductOption> added = new ArrayList<>();
        List<ProductOption> requested = new ArrayList<>();
        for (ProductOption prepared : options) {
            ProductOption matched = bySet.remove(valueSet(prepared));
            if (matched == null) {
                added.add(prepared);
                requested.add(prepared);
                continue;
            }
            matched.replaceSkuAndPrice(prepared.getSkuCode(), prepared.getPrice());
            requested.add(matched);
        }
        List<ProductOption> removed = new ArrayList<>(bySet.values());
        current.removeAll(removed);
        current.addAll(added);
        return new UpdateResult(true, removed, requested);
    }

    // 사이즈 값이 같은 실측은 객체를 그대로 두고 권장 키와 실측 값만 맞추고, 미리 만든 실측에 없는 사이즈의 실측은 빼고,
    // 지금 없는 사이즈의 실측은 미리 만든 객체를 그대로 넣는다
    private void replaceSizeSpecs(List<SizeSpec> current) {
        Map<AttributeValue, SizeSpec> bySize = bySize(current);
        List<SizeSpec> added = new ArrayList<>();
        for (SizeSpec prepared : sizeSpecs) {
            SizeSpec matched = bySize.remove(prepared.getSizeValue());
            if (matched == null) {
                added.add(prepared);
                continue;
            }
            matched.replaceValues(prepared.getMinHeightCm(), prepared.getMaxHeightCm(), prepared.getMeasurements());
        }
        current.removeAll(bySize.values());
        current.addAll(added);
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

    // 실측을 사이즈 값으로 찾을 수 있게 모은다
    private static Map<AttributeValue, SizeSpec> bySize(List<SizeSpec> sizeSpecs) {
        Map<AttributeValue, SizeSpec> bySize = new HashMap<>();
        for (SizeSpec sizeSpec : sizeSpecs) {
            bySize.put(sizeSpec.getSizeValue(), sizeSpec);
        }
        return bySize;
    }

    // 실측 값은 크기로 비교한다. 42와 42.0은 같은 길이라 같은 값으로 본다. 같은 요청을 다시 보냈을 때
    // 자릿수만 다른 값 때문에 바뀐 것으로 보지 않게 하기 위해서다
    private static boolean sameMeasurements(Map<String, BigDecimal> current, Map<String, BigDecimal> requested) {
        if (!current.keySet().equals(requested.keySet())) {
            return false;
        }
        for (Map.Entry<String, BigDecimal> entry : requested.entrySet()) {
            if (current.get(entry.getKey()).compareTo(entry.getValue()) != 0) {
                return false;
            }
        }
        return true;
    }
}
