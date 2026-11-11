package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

public enum GirthPart {
    CHEST,
    WAIST,
    HIP;

    /**
     * 실측 항목 코드에 맞는 둘레 부위를 돌려준다. 둘레 비율이 없는 항목(어깨너비, 길이 항목)은 null이다.
     * 너비 항목은 단면이라 몸 둘레의 절반에 해당한다.
     */
    public static GirthPart ofWidthCode(String code) {
        return switch (code) {
            case "CHEST_WIDTH" -> CHEST;
            case "WAIST_WIDTH" -> WAIST;
            case "HIP_WIDTH" -> HIP;
            default -> null;
        };
    }
}
