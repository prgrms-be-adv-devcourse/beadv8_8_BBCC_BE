package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.util.List;

/**
 * 판매자가 보는 내 상품 상세이다.
 *
 * @param brand 없으면 null
 * @param description 없으면 null
 * @param thumbnailUrl 없으면 null
 * @param imageUrls 저장된 순서대로 담고, 없으면 빈 목록
 * @param options 상품에 저장된 순서대로 담는다
 */
public record SellerProductDetailResult(
    Long productId, String name, String status, Long version,
    String brand, Long categoryId, String description, String thumbnailUrl, List<String> imageUrls,
    List<Option> options) {

    /**
     * @param availableQuantity 옵션의 판매 가능 수량. 재고 행이 없으면 0
     * @param skuCode 없으면 null
     */
    public record Option(
        Long optionId, String optionName, long price, int availableQuantity, String saleStatus, String skuCode) {
    }
}
