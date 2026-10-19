package com.bbcc.kidly.boundedcontext.childprofile.domain;

/**
 * 자녀 프로필 도메인의 Kidly 정책 값. 요청 DTO의 Bean Validation과 컬럼 길이가 함께 쓴다.
 * 키 · 몸무게는 소수 첫째 자리까지만 받는다.
 */
public final class ChildProfilePolicy {

    public static final int NAME_MAX_LENGTH = 50;
    public static final String HEIGHT_CM_MIN = "40.0";
    public static final String HEIGHT_CM_MAX = "200.0";
    public static final String WEIGHT_KG_MIN = "2.0";
    public static final String WEIGHT_KG_MAX = "150.0";

    private ChildProfilePolicy() {
    }
}
