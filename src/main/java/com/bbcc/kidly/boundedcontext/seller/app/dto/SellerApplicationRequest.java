package com.bbcc.kidly.boundedcontext.seller.app.dto;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationSubmitInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerApplicationRequest(
    @NotBlank @Size(max = SellerApplication.NAME_MAX_LENGTH) String businessName,
    @NotBlank @Size(max = SellerApplication.NAME_MAX_LENGTH) String representativeName,
    @NotNull @Pattern(regexp = SellerApplication.BUSINESS_REGISTRATION_NUMBER_REGEX,
        message = "하이픈 없는 숫자 10자리여야 합니다") String businessRegistrationNumber) {

    public SellerApplicationSubmitInfo toSubmitInfo(Long applicantId, String applicantNickname) {
        return new SellerApplicationSubmitInfo(
            applicantId, applicantNickname, businessName, representativeName, businessRegistrationNumber);
    }
}
