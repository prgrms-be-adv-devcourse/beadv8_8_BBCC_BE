package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerSuspendRequest(@NotBlank @Size(max = SellerPolicy.SUSPEND_REASON_MAX_LENGTH) String reason) {
}
