package com.bbcc.kidly.boundedcontext.member.domain;

/**
 * 회원 도메인의 Kidly 정책 값. 요청 DTO의 Bean Validation과 컬럼 길이가 함께 쓴다.
 * 닉네임 규칙은 {@link Nickname}, 닉네임 중복은 {@link NicknamePolicy}, 기본 배송지는 {@link DefaultShippingAddressPolicy}.
 */
public final class MemberPolicy {

    public static final int SUSPEND_REASON_MAX_LENGTH = 200;

    // 배송지
    public static final int RECIPIENT_NAME_MAX_LENGTH = 50;
    public static final String RECIPIENT_PHONE_REGEX = "0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4}";
    public static final int RECIPIENT_PHONE_MAX_LENGTH = 13;
    public static final int ZIP_CODE_MAX_LENGTH = 10;
    public static final int ROAD_ADDRESS_MAX_LENGTH = 200;
    public static final int DETAIL_ADDRESS_MAX_LENGTH = 100;

    private MemberPolicy() {
    }
}
