package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 기준 옷과 상품 사이즈별 실측의 차이다. 차이는 상품 사이즈 실측에서 기준 옷 실측을 뺀 cm라서,
 * 양수면 그 사이즈가 기준 옷보다 크고 음수면 작다.
 *
 * @param productPart 사려는 상품의 종류에 맞는 칸이다
 * @param comparedPart 실제로 비교한 기준 옷의 칸이다. 상품과 같은 종류의 칸이 비어 다른 칸과 비교했으면 productPart와 다르다
 */
public record ReferenceFit(GarmentPart productPart, GarmentPart comparedPart, List<Item> items) {

    // 항목 순서는 응답에서 늘 같게 보이도록 고정한다.
    private static final List<String> ITEM_ORDER = List.of("TOTAL_LENGTH", "CHEST_WIDTH", "SHOULDER_WIDTH",
        "SLEEVE_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "RISE");

    // 길이 항목은 같은 세부 종류끼리만 견줄 수 있어서, 기준 옷과 상품의 카테고리가 다르면 비교에서 뺀다.
    // 나머지 항목은 몸통에 맞는지를 보여 주는 너비 항목이라 옷 종류가 달라도 비교한다.
    private static final Set<String> LENGTH_CODES = Set.of("TOTAL_LENGTH", "SLEEVE_LENGTH", "RISE");

    public ReferenceFit {
        requireNonNull(productPart, "productPart는 필수입니다");
        requireNonNull(comparedPart, "comparedPart는 필수입니다");
        items = List.copyOf(requireNonNull(items, "items는 필수입니다"));
    }

    /**
     * 저장된 AI 답을 찾는 키에 넣을 글이다. 기준 핏이 같으면 늘 같은 글이 되도록 항목과 사이즈를 정해진 순서로 쓰고,
     * 2와 2.0이 같은 글이 되도록 숫자의 끝의 0을 뗀다.
     */
    public String keyText() {
        StringBuilder text = new StringBuilder();
        text.append(productPart).append('>').append(comparedPart);
        for (Item item : items) {
            text.append(item.keyText());
        }
        return text.toString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 실측 항목 하나의 기준 옷 값과, 그 값에 대한 사이즈별 차이다.
     */
    public record Item(String code, BigDecimal garmentCm, List<SizeDiff> sizes) {

        public Item {
            sizes = List.copyOf(requireNonNull(sizes, "sizes는 필수입니다"));
        }

        /**
         * AI 답 키에 넣을 글이다. 「;코드=옷 실측」 뒤에 사이즈마다 「,사이즈:상품 실측:차이」를 쓰고, 숫자 끝의 0은 뗀다.
         */
        public String keyText() {
            StringBuilder text = new StringBuilder();
            text.append(';').append(code).append('=').append(plain(garmentCm));
            for (SizeDiff diff : sizes) {
                text.append(',').append(diff.size()).append(':').append(plain(diff.productCm()))
                    .append(':').append(plain(diff.diffCm()));
            }
            return text.toString();
        }
    }

    /**
     * 사이즈 하나의 상품 실측과 기준 옷과의 차이다.
     */
    public record SizeDiff(String size, BigDecimal productCm, BigDecimal diffCm) {
    }

    /**
     * 상품과 같은 종류의 칸이 비어 다른 칸의 기준 옷과 비교했는지 돌려준다.
     */
    public boolean substituted() {
        return productPart != comparedPart;
    }

    /**
     * 그 항목 · 그 사이즈의 차이(상품 − 기준 옷, cm)를 돌려준다. 비교하지 않은 항목이거나 그 사이즈가 없으면 null이다.
     */
    public BigDecimal diffOf(String code, String size) {
        for (Item item : items) {
            if (!item.code().equals(code)) {
                continue;
            }
            for (SizeDiff diff : item.sizes()) {
                if (diff.size().equals(size)) {
                    return diff.diffCm();
                }
            }
        }
        return null;
    }

    public static Optional<ReferenceFit> compare(FitProduct product, List<ReferenceGarment> garments) {
        requireNonNull(product, "product는 필수입니다");
        requireNonNull(garments, "garments는 필수입니다");
        // 사이즈 무관 상품은 고를 사이즈가 없어서 비교하지 않는다.
        if (product.sizeFree()) {
            return Optional.empty();
        }
        GarmentPart productPart = GarmentPart.ofCategoryPath(product.categoryPathNames());
        // 모자 · 양말처럼 기준 옷 칸이 없는 종류는 비교하지 않는다.
        if (productPart == null) {
            return Optional.empty();
        }
        ReferenceGarment garment = garmentOf(productPart, garments);
        GarmentPart substitute = productPart.substitute();
        if (garment == null && substitute != null) {
            garment = garmentOf(substitute, garments);
        }
        if (garment == null) {
            return Optional.empty();
        }
        List<Item> items = itemsOf(product, garment.getCategoryId(), garment.getMeasurements());
        // 견줄 항목이 하나도 없으면 기준 핏으로 알려 줄 것이 없다.
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReferenceFit(productPart, garment.getPart(), items));
    }

    private static ReferenceGarment garmentOf(GarmentPart part, List<ReferenceGarment> garments) {
        for (ReferenceGarment garment : garments) {
            if (garment.getPart() == part) {
                return garment;
            }
        }
        return null;
    }

    /**
     * 지금 상품의 사이즈별 실측과 옷 한 벌의 실측을 견줘 항목마다 차이를 돌려준다. 기준 옷뿐 아니라 지난 구매에서 산 옷과도 같은 방식으로 비교한다.
     *
     * @param garmentCategoryId 견줄 옷의 카테고리 ID다. 상품과 같을 때만 길이 항목을 비교한다
     * @param garmentMeasurements 견줄 옷의 실측이다. 키는 실측 항목 코드, 값은 cm다
     */
    public static List<Item> itemsOf(FitProduct product, Long garmentCategoryId,
        Map<String, BigDecimal> garmentMeasurements) {
        // 상품의 카테고리를 모르면(null) 같은 세부 종류인지 알 수 없으므로 다른 카테고리로 본다.
        boolean sameCategory = Objects.equals(garmentCategoryId, product.categoryId());
        List<Item> items = new ArrayList<>();
        for (String code : ITEM_ORDER) {
            BigDecimal garmentCm = garmentMeasurements.get(code);
            // 보호자가 입력하지 않은 선택 항목은 비교할 수 없다.
            if (garmentCm == null) {
                continue;
            }
            if (LENGTH_CODES.contains(code) && !sameCategory) {
                continue;
            }
            List<SizeDiff> diffs = diffsOf(product, code, garmentCm);
            if (!diffs.isEmpty()) {
                items.add(new Item(code, garmentCm, diffs));
            }
        }
        return items;
    }

    private static List<SizeDiff> diffsOf(FitProduct product, String code, BigDecimal garmentCm) {
        List<SizeDiff> diffs = new ArrayList<>();
        for (FitProduct.SizeMeasurements size : product.sizes()) {
            BigDecimal productCm = size.measurements().get(code);
            // 판매자가 이 사이즈의 이 항목을 재지 않았으면 비교할 수 없으므로 건너뛴다.
            if (productCm == null) {
                continue;
            }
            diffs.add(new SizeDiff(size.size(), productCm, productCm.subtract(garmentCm)));
        }
        return diffs;
    }
}
