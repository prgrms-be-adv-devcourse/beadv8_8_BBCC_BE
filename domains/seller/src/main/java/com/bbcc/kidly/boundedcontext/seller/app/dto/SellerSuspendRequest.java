package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 정지 사유. 저장만 하고 아직 응답하지 않는다
 */
public record SellerSuspendRequest(@NotBlank @Size(max = SellerPolicy.SUSPEND_REASON_MAX_LENGTH) String reason) {
}
