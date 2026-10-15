package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;

/**
 * @param email 이메일을 받기 전에 가입한 회원은 null
 */
public record AdminMemberSummaryResponse(
    Long id, String nickname, String email, String status, SellerStatus sellerStatus,
    String suspendReason, LocalDateTime suspendedAt, LocalDateTime createdAt) {

    public static AdminMemberSummaryResponse from(AdminMemberSummary summary) {
        return new AdminMemberSummaryResponse(summary.id(), summary.nickname(), summary.email(),
            summary.status(), summary.sellerStatus(), summary.suspendReason(), summary.suspendedAt(),
            summary.createdAt());
    }
}
