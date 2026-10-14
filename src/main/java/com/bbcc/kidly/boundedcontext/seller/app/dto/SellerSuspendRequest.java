package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerSuspension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason 정지 사유. 저장만 하고 아직 응답하지 않는다
 */
public record SellerSuspendRequest(@NotBlank @Size(max = SellerSuspension.REASON_MAX_LENGTH) String reason) {
}
