package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import com.bbcc.kidly.shared.childprofile.domain.Gender;

/**
 * 만 나이 · 성별의 「높이 항목 ÷ 키」 비율이다.
 *
 * @param neck 목뒤높이
 * @param shoulder 어깨높이
 * @param waist 허리높이
 * @param hip 엉덩이높이
 * @param crotch 샅높이
 * @param knee 무릎높이
 * @param ankle 가쪽복사높이
 * @param arm 팔길이
 */
public record BodyHeightRatioRow(
        Gender gender,
        int ageYears,
        double neck,
        double shoulder,
        double waist,
        double hip,
        double crotch,
        double knee,
        double ankle,
        double arm
) {
}
