package com.bbcc.kidly.boundedcontext.seller.domain;

public record SellerApplicationSubmitInfo(Long applicantId, String businessName, String representativeName,
    String businessRegistrationNumber) {
}
