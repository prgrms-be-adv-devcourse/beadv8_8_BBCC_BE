package com.bbcc.kidly.boundedcontext.seller.in.dto;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.time.LocalDateTime;

/**
 * @param decidedAt 심사 전이면 null
 */
public record SellerApplicationResponse(
    Long id,
    String businessName,
    String representativeName,
    String businessRegistrationNumber,
    SellerApplicationStatus status,
    LocalDateTime createdAt,
    LocalDateTime decidedAt) {

    public static SellerApplicationResponse from(SellerApplicationResult result) {
        return new SellerApplicationResponse(
            result.id(), result.businessName(), result.representativeName(), result.businessRegistrationNumber(),
            result.status(), result.createdAt(), result.decidedAt());
    }
}
