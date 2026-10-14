package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.time.LocalDateTime;

public record SellerApplicationResult(
    Long id,
    String businessName,
    String representativeName,
    String maskedBusinessRegistrationNumber,
    SellerApplicationStatus status,
    LocalDateTime createdAt,
    LocalDateTime decidedAt) {

    public static SellerApplicationResult from(SellerApplication application) {
        return new SellerApplicationResult(
            application.getId(), application.getBusinessName(), application.getRepresentativeName(),
            application.maskedBusinessRegistrationNumber(), application.getStatus(), application.getCreatedAt(),
            application.getDecidedAt());
    }
}
