package com.bbcc.kidly.boundedcontext.seller.in.dto;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.time.LocalDateTime;

public record SellerApplicationSummaryResponse(
    Long id, String businessName, Long applicantId, String applicantNickname,
    String maskedBusinessRegistrationNumber,
    SellerApplicationStatus status, LocalDateTime createdAt) {

    public static SellerApplicationSummaryResponse from(SellerApplicationSummary summary) {
        return new SellerApplicationSummaryResponse(summary.id(), summary.businessName(), summary.applicantId(),
            summary.applicantNickname(), summary.maskedBusinessRegistrationNumber(), summary.status(),
            summary.createdAt());
    }
}
