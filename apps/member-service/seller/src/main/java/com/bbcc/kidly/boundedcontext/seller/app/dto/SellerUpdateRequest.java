package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerUpdateRequest(@NotBlank @Size(max = SellerPolicy.NAME_MAX_LENGTH) String businessName) {
}
