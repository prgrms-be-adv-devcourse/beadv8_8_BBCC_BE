package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.AgeGroup;
import com.bbcc.kidly.boundedcontext.recommend.domain.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRecommender;
import com.bbcc.kidly.boundedcontext.recommend.domain.WeightPercentiles;
import com.bbcc.kidly.shared.member.domain.BodyType;
import com.bbcc.kidly.shared.member.domain.Gender;
import com.bbcc.kidly.shared.member.domain.PreferredFit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class RecommendFixture {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    /**
     * 기획서 7.1 자녀 1의 생년월일. 2026-10-12 기준 만 7세라 3~18세 표를 쓴다.
     */
    public static final LocalDate CHILD_1_BIRTH_DATE = LocalDate.of(2019, 1, 1);

    private RecommendFixture() {
    }

    public static SizeRecommender recommender() {
        return new SizeRecommender(growthChart());
    }

    /**
     * 테스트용 체형표. 질병관리청 2017 성장도표 3~18세 남자 115cm 행(15백분위 19.1kg, 85백분위 22.7kg)만 있다.
     * 다른 키는 표에 없어서, 계산은 보호자가 입력한 체형을 그대로 쓴다.
     */
    public static GrowthChart growthChart() {
        return (ageGroup, gender, heightCm) -> {
            if (ageGroup == AgeGroup.YEARS_3_18 && gender == Gender.MALE && heightCm == 115.0) {
                return Optional.of(WeightPercentiles.of(19.1, 22.7));
            }
            return Optional.empty();
        };
    }

    /**
     * 기획서 7.1 자녀 1: 2019-01-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static ChildSizeInput childSizeInput() {
        return childSizeInputWithHeight(115.0);
    }

    public static ChildSizeInput childSizeInputWith(
        double heightCm, double weightKg, BodyType bodyType, PreferredFit preferredFit) {
        return ChildSizeInput.of(heightCm, weightKg, Gender.MALE, CHILD_1_BIRTH_DATE, bodyType, preferredFit);
    }

    public static ChildSizeInput childSizeInputWithHeight(double heightCm) {
        return ChildSizeInput.of(heightCm, 20.0, Gender.MALE,
                CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE);
    }

    /**
     * 기획서 7.1 상품 A: 110(권장 키 105~115cm), 120(115~125cm).
     */
    public static ProductSizes productA() {
        return ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125)));
    }
}
