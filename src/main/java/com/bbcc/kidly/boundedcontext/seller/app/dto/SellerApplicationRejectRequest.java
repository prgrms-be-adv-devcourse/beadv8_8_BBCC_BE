package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 신청한 회원에게 보여 줄 거절 사유
 */
public record SellerApplicationRejectRequest(
    @NotBlank @Size(max = SellerApplication.REJECT_REASON_MAX_LENGTH) String reason) {
}
