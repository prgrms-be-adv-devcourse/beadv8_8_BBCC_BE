package com.bbcc.kidly.boundedcontext.seller.domain;

public final class SellerPolicy {

    public static final int NAME_MAX_LENGTH = 50;

    public static final int BUSINESS_REGISTRATION_NUMBER_LENGTH = 10;
    public static final String BUSINESS_REGISTRATION_NUMBER_REGEX = "[0-9]{10}";

    public static final int REJECT_REASON_MAX_LENGTH = 200;
    public static final int SUSPEND_REASON_MAX_LENGTH = 200;

    private SellerPolicy() {
    }

    public static String maskBusinessRegistrationNumber(String number) {
        return number.substring(0, 3) + "-" + number.charAt(3) + "*-*****";
    }
}
