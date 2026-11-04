package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;

public record SellerResult(Long sellerId, String businessName, SellerStatus status) {

    public static SellerResult from(Seller seller) {
        return new SellerResult(seller.getId(), seller.getBusinessName(), seller.getStatus());
    }
}
