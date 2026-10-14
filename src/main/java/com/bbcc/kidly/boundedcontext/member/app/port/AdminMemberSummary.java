package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;

/**
 * 관리자 회원 목록의 한 행.
 *
 * @param status 회원 상태(MemberStatus 이름: ACTIVE · SUSPENDED)
 * @param sellerStatus 판매자가 아니면 null
 * @param suspendReason 이용 정지된 회원만 값이 있다
 * @param suspendedAt 이용 정지된 회원만 값이 있다
 */
public record AdminMemberSummary(
    Long id, String nickname, String status, SellerStatus sellerStatus,
    String suspendReason, LocalDateTime suspendedAt, LocalDateTime createdAt) {

    public static AdminMemberSummary from(Member member) {
        var suspension = member.getSuspension();
        return new AdminMemberSummary(member.getId(), member.getNickname(), member.getStatus().name(),
            member.sellerStatus(),
            suspension == null ? null : suspension.reason(),
            suspension == null ? null : suspension.suspendedAt(),
            member.getCreatedAt());
    }
}
