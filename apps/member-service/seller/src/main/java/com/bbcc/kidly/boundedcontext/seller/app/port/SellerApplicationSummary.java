package com.bbcc.kidly.boundedcontext.seller.app.port;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.time.LocalDateTime;

/**
 * 관리자 신청 목록의 한 행. 승인 · 거절 응답도 같은 모양이다.
 *
 * @param maskedBusinessRegistrationNumber 앞 4자리만 보이게 가린 사업자등록번호 (예: 214-8*-*****)
 */
public record SellerApplicationSummary(
    Long id, String businessName, Long applicantId, String applicantNickname,
    String maskedBusinessRegistrationNumber, SellerApplicationStatus status, LocalDateTime createdAt) {

    public static SellerApplicationSummary from(SellerApplication application) {
        return new SellerApplicationSummary(application.getId(), application.getBusinessName(),
            application.getApplicantId(), application.getApplicantNickname(),
            application.maskedBusinessRegistrationNumber(), application.getStatus(), application.getCreatedAt());
    }
}
