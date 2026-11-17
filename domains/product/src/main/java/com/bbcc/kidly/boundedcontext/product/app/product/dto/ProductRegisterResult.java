package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.util.List;

public record ProductRegisterResult(Long productId, String status, Long version, List<Option> options) {

    public record Option(
        Long optionId, String optionName, long price, int availableQuantity, String saleStatus, String skuCode) {
    }
}
