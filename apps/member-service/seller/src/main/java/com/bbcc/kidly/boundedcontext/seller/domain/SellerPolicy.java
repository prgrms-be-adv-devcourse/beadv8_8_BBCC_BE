package com.bbcc.kidly.boundedcontext.seller.domain;

/**
 * 판매자 도메인의 Kidly 정책 값. 요청 DTO의 Bean Validation과 컬럼 길이가 함께 쓴다.
 */
public final class SellerPolicy {

    // 상호명 · 대표자명
    public static final int NAME_MAX_LENGTH = 50;

    // 하이픈 없는 ASCII 숫자 10자리. 진위 확인(사업자 인증)은 MVP에서 빠졌다
    public static final int BUSINESS_REGISTRATION_NUMBER_LENGTH = 10;
    public static final String BUSINESS_REGISTRATION_NUMBER_REGEX = "[0-9]{10}";

    public static final int REJECT_REASON_MAX_LENGTH = 200;
    public static final int SUSPEND_REASON_MAX_LENGTH = 200;

    private SellerPolicy() {
    }

    // 신청 응답 · 관리자 목록용. 앞 4자리만 보이게 가린다 (예: 2148612345 → 214-8*-*****)
    public static String maskBusinessRegistrationNumber(String number) {
        return number.substring(0, 3) + "-" + number.charAt(3) + "*-*****";
    }
}
