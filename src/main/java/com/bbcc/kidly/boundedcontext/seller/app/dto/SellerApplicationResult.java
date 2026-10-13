package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.time.LocalDateTime;

public record SellerApplicationResult(
    Long id,
    String businessName,
    String representativeName,
    String businessRegistrationNumber,
    SellerApplicationStatus status,
    LocalDateTime createdAt,
    LocalDateTime decidedAt) {

    // 신청자 본인이 보는 값이라 사업자등록번호를 마스킹하지 않는다
    public static SellerApplicationResult from(SellerApplication application) {
        return new SellerApplicationResult(
            application.getId(), application.getBusinessName(), application.getRepresentativeName(),
            application.getBusinessRegistrationNumber(), application.getStatus(), application.getCreatedAt(),
            application.getDecidedAt());
    }
}
