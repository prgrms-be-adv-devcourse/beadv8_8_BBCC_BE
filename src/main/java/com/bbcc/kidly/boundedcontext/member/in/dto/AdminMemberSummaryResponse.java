package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;

public record AdminMemberSummaryResponse(
    Long id, String nickname, String status, SellerStatus sellerStatus,
    String suspendReason, LocalDateTime suspendedAt, LocalDateTime createdAt) {

    public static AdminMemberSummaryResponse from(AdminMemberSummary summary) {
        return new AdminMemberSummaryResponse(summary.id(), summary.nickname(), summary.status(),
            summary.sellerStatus(), summary.suspendReason(), summary.suspendedAt(), summary.createdAt());
    }
}
