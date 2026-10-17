package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 판매자가 보는 내 상품 상세.
 *
 * @param brand 없으면 null
 * @param description 없으면 null
 * @param thumbnailUrl 없으면 null
 * @param imageUrls 저장된 순서대로 담고, 없으면 빈 목록
 * @param options 상품에 저장된 순서대로 담는다
 */
public record SellerProductDetailResponse(
    String productId, String name, String status, Long version,
    String brand, String categoryId, String description, String thumbnailUrl, List<String> imageUrls,
    List<Option> options) {

    /**
     * @param skuCode 없으면 null
     */
    public record Option(
        String optionId, String optionName, long price, int availableQuantity, String saleStatus, String skuCode) {
    }

    public static SellerProductDetailResponse from(SellerProductDetailResult result) {
        List<Option> options = new ArrayList<>();
        for (SellerProductDetailResult.Option option : result.options()) {
            options.add(new Option(
                String.valueOf(option.optionId()),
                option.optionName(),
                option.price(),
                option.availableQuantity(),
                option.saleStatus(),
                option.skuCode()));
        }
        return new SellerProductDetailResponse(
            String.valueOf(result.productId()), result.name(), result.status(), result.version(),
            result.brand(), String.valueOf(result.categoryId()), result.description(), result.thumbnailUrl(),
            result.imageUrls(), options);
    }
}
