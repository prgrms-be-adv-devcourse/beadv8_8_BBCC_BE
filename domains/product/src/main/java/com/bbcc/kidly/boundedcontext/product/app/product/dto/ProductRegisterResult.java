package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.util.List;

/**
 * 등록한 상품이다.
 *
 * @param options 요청한 순서대로 담는다
 */
public record ProductRegisterResult(Long productId, String status, Long version, List<Option> options) {

    /**
     * @param availableQuantity 등록할 때 넣은 초기 재고
     * @param skuCode 없으면 null
     */
    public record Option(
        Long optionId, String optionName, long price, int availableQuantity, String saleStatus, String skuCode) {
    }
}
