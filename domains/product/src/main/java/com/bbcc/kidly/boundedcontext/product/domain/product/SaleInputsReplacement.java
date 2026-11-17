package com.bbcc.kidly.boundedcontext.product.domain.product;

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

final class SaleInputsReplacement {

    private final List<ProductOption> options;
    private final List<SizeSpec> sizeSpecs;

    private SaleInputsReplacement(List<ProductOption> options, List<SizeSpec> sizeSpecs) {
        this.options = options;
        this.sizeSpecs = sizeSpecs;
    }

    static SaleInputsReplacement prepare(Product product, SaleInputsInfo saleInputs, Category newCategory) {
        ProductType productType = newCategory.productTypeToRegister();
        ProductSizeSpecs.requireNonNullValues(saleInputs.sizeSpecs());
        if (!saleInputs.options().isEmpty()) {
            ProductOptions.validateNewOptions(saleInputs.options(), saleInputs.prices(), List.of(), productType);
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
        SaleStartConditions.requireComplete(options, sizeSpecs, newCategory, productType);
        return new SaleInputsReplacement(options, sizeSpecs);
    }

    boolean differsFrom(List<ProductOption> currentOptions, List<SizeSpec> currentSizeSpecs) {
        return differsOptions(currentOptions) || differsSizeSpecs(currentSizeSpecs);
    }

    List<ProductOption> matchedOptions(List<ProductOption> currentOptions) {
        Map<Set<AttributeValue>, ProductOption> bySet = activeBySet(currentOptions);
        List<ProductOption> matched = new ArrayList<>();
        for (ProductOption prepared : options) {
            matched.add(bySet.get(valueSet(prepared)));
        }
        return matched;
    }

    UpdateResult replace(List<ProductOption> currentOptions, List<SizeSpec> currentSizeSpecs) {
        UpdateResult result = replaceOptions(currentOptions);
        replaceSizeSpecs(currentSizeSpecs);
        return result;
    }

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
