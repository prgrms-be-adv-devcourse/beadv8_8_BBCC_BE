package com.bbcc.kidly.boundedcontext.product;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProductRegisterRequestFixture {

    private ProductRegisterRequestFixture() {
    }

    static Map<String, Object> productA(SavedClothingTop saved) {
        ClothingOptions clothing = saved.clothing();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "상품 A");
        body.put("brand", "키들리");
        body.put("categoryId", id(saved.top().getId()));
        body.put("description", "상품 설명");
        body.put("thumbnailUrl", null);
        body.put("imageUrls", new ArrayList<>());
        body.put("attributes", new ArrayList<>(List.of(attribute(clothing.mainMaterial().getId(),
            List.of(id(clothing.cotton().getId()))))));
        body.put("options", new ArrayList<>(List.of(
            option(List.of(id(clothing.navy().getId()), id(clothing.size110().getId())), 10, "A-NV-110"),
            option(List.of(id(clothing.navy().getId()), id(clothing.size120().getId())), 0, null))));
        body.put("prices", new ArrayList<>(List.of(price(List.of(), 25000))));
        body.put("sizeSpecs", new ArrayList<>(List.of(
            sizeSpec(clothing.size110().getId(), 105, 115, 42, 30, 34, 38),
            sizeSpec(clothing.size120().getId(), 115, 125, 45, 32, 36, 41))));
        return body;
    }

    static String ownImageUrl(long sellerId) {
        return "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/test/products/" + sellerId
            + "/0b8c1e2a-3d4f-4a5b-9c6d-7e8f9a0b1c2d.jpg";
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> attributes(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("attributes");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> options(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("options");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> prices(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("prices");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> sizeSpecs(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("sizeSpecs");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> measurements(Map<String, Object> sizeSpec) {
        return (Map<String, Object>) sizeSpec.get("measurements");
    }

    static Map<String, Object> attribute(Long attributeId, List<String> valueIds) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("attributeId", id(attributeId));
        attribute.put("valueIds", valueIds);
        attribute.put("text", null);
        return attribute;
    }

    static Map<String, Object> option(List<String> valueIds, int initialQuantity, String skuCode) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("valueIds", valueIds);
        option.put("initialQuantity", initialQuantity);
        option.put("skuCode", skuCode);
        return option;
    }

    static Map<String, Object> price(List<String> valueIds, long price) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("valueIds", valueIds);
        group.put("price", price);
        return group;
    }

    static Map<String, Object> sizeSpec(Long sizeValueId, int minHeightCm, int maxHeightCm,
        int totalLength, int shoulderWidth, int chestWidth, int sleeveLength) {
        Map<String, Object> measurements = new LinkedHashMap<>();
        measurements.put("TOTAL_LENGTH", totalLength);
        measurements.put("SHOULDER_WIDTH", shoulderWidth);
        measurements.put("CHEST_WIDTH", chestWidth);
        measurements.put("SLEEVE_LENGTH", sleeveLength);
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("sizeValueId", id(sizeValueId));
        spec.put("minHeightCm", minHeightCm);
        spec.put("maxHeightCm", maxHeightCm);
        spec.put("measurements", measurements);
        return spec;
    }

    static String id(Long id) {
        return String.valueOf(id);
    }
}
