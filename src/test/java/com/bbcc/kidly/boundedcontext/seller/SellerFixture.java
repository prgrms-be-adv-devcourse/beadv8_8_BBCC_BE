package com.bbcc.kidly.boundedcontext.seller;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationSubmitInfo;
import java.time.LocalDateTime;

public final class SellerFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 9, 0);
    public static final Long ADMIN_ID = 99L;
    public static final String APPLICANT_NICKNAME = "소소맘";
    public static final String BUSINESS_NAME = "소소한옷장";
    public static final String REPRESENTATIVE_NAME = "홍길동";
    public static final String BUSINESS_REGISTRATION_NUMBER = "2148612345";

    public static final String MASKED_BUSINESS_REGISTRATION_NUMBER = "214-8*-*****";

    private SellerFixture() {
    }

    public static SellerApplicationSubmitInfo submitInfo(Long applicantId) {
        return new SellerApplicationSubmitInfo(applicantId, APPLICANT_NICKNAME, BUSINESS_NAME,
            REPRESENTATIVE_NAME, BUSINESS_REGISTRATION_NUMBER);
    }

    public static SellerApplication application(Long applicantId) {
        return SellerApplication.submit(submitInfo(applicantId));
    }

    public static SellerApplication approvedApplication(Long applicantId) {
        var application = application(applicantId);
        application.approve(ADMIN_ID, NOW);
        return application;
    }
}
