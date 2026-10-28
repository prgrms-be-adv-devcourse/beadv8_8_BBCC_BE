package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @param options 요청한 순서대로 담는다
 */
public record ProductRegisterResponse(String productId, String status, Long version, List<Option> options) {

    /**
     * @param skuCode 없으면 null
     */
    public record Option(
        String optionId, String optionName, long price, int availableQuantity, String saleStatus, String skuCode) {
    }

    public static ProductRegisterResponse from(ProductRegisterResult result) {
        List<Option> options = new ArrayList<>();
        for (ProductRegisterResult.Option option : result.options()) {
            options.add(new Option(
                String.valueOf(option.optionId()),
                option.optionName(),
                option.price(),
                option.availableQuantity(),
                option.saleStatus(),
                option.skuCode()));
        }
        return new ProductRegisterResponse(
            String.valueOf(result.productId()), result.status(), result.version(), options);
    }
}
