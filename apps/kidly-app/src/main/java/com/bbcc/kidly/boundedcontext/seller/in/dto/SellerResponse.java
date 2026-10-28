package com.bbcc.kidly.boundedcontext.seller.in.dto;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerResult;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;

public record SellerResponse(Long sellerId, String businessName, SellerStatus status) {

    public static SellerResponse from(SellerResult result) {
        return new SellerResponse(result.sellerId(), result.businessName(), result.status());
    }
}
