package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 신청한 회원에게 보여 줄 거절 사유
 */
public record SellerApplicationRejectRequest(
    @NotBlank @Size(max = SellerPolicy.REJECT_REASON_MAX_LENGTH) String reason) {
}
