package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 기준 옷과 상품 사이즈별 실측의 차이다. 차이는 상품 사이즈 실측에서 기준 옷 실측을 뺀 cm라서,
 * 양수면 그 사이즈가 기준 옷보다 크고 음수면 작다.
 */
public record ReferenceFit(GarmentPart part, boolean substituted, List<Item> items) {

    // 항목 순서는 응답에서 늘 같게 보이도록 고정한다.
    private static final List<String> ITEM_ORDER = List.of("TOTAL_LENGTH", "CHEST_WIDTH", "SHOULDER_WIDTH",
        "SLEEVE_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "RISE");

    // 길이 항목은 같은 세부 종류끼리만 견줄 수 있어서, 기준 옷과 상품의 카테고리가 다르면 비교에서 뺀다.
    private static final Set<String> LENGTH_CODES = Set.of("TOTAL_LENGTH", "SLEEVE_LENGTH", "RISE");

    /**
     * 실측 항목 하나의 기준 옷 값과, 그 값에 대한 사이즈별 차이다.
     */
    public record Item(String code, BigDecimal garmentCm, List<SizeDiff> sizes) {
    }

    /**
     * 사이즈 하나의 상품 실측과 기준 옷과의 차이다.
     */
    public record SizeDiff(String size, BigDecimal productCm, BigDecimal diffCm) {
    }

    public static Optional<ReferenceFit> compare(FitProduct product, List<ReferenceGarment> garments) {
        // 사이즈 무관 상품은 고를 사이즈가 없어서 비교하지 않는다
        if (product.sizeFree()) {
            return Optional.empty();
        }
        GarmentPart part = GarmentPart.of(product.categoryPathNames());
        // 모자 · 양말처럼 기준 옷 칸이 없는 종류는 비교하지 않는다
        if (part == null) {
            return Optional.empty();
        }
        ReferenceGarment garment = garmentOf(part, garments);
        boolean substituted = false;
        GarmentPart substitute = part.substitute();
        if (garment == null && substitute != null) {
            part = substitute;
            garment = garmentOf(part, garments);
            substituted = true;
        }
        if (garment == null) {
            return Optional.empty();
        }
        boolean sameCategory = garment.getCategoryId().equals(product.categoryId());
        List<Item> items = new ArrayList<>();
        for (String code : ITEM_ORDER) {
            BigDecimal garmentCm = garment.getMeasurements().get(code);
            if (garmentCm == null || (LENGTH_CODES.contains(code) && !sameCategory)) {
                continue;
            }
            List<SizeDiff> diffs = diffsOf(product, code, garmentCm);
            if (!diffs.isEmpty()) {
                items.add(new Item(code, garmentCm, diffs));
            }
        }
        // 견줄 항목이 하나도 없으면 기준 핏으로 알려 줄 것이 없다
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReferenceFit(part, substituted, items));
    }

    private static ReferenceGarment garmentOf(GarmentPart part, List<ReferenceGarment> garments) {
        for (ReferenceGarment garment : garments) {
            if (garment.getPart() == part) {
                return garment;
            }
        }
        return null;
    }

    private static List<SizeDiff> diffsOf(FitProduct product, String code, BigDecimal garmentCm) {
        List<SizeDiff> diffs = new ArrayList<>();
        for (FitProduct.SizeMeasurements size : product.sizes()) {
            BigDecimal productCm = size.measurements().get(code);
            // 판매자가 이 사이즈의 이 항목을 재지 않았으면 비교할 수 없으므로 건너뛴다
            if (productCm == null) {
                continue;
            }
            diffs.add(new SizeDiff(size.size(), productCm, productCm.subtract(garmentCm)));
        }
        return diffs;
    }
}
