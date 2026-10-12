package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.BodyType;
import com.bbcc.kidly.boundedcontext.recommend.domain.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.Gender;
import com.bbcc.kidly.boundedcontext.recommend.domain.PreferredFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.SizeRecommender;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class RecommendFixture {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private RecommendFixture() {
    }

    public static SizeRecommender recommender() {
        return new SizeRecommender();
    }

    /**
     * 기획서 7.1 자녀 1: 2019-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static ChildSizeInput childSizeInput() {
        return childSizeInputWithHeight(115.0);
    }

    public static ChildSizeInput childSizeInputWith(
        double heightCm, double weightKg, BodyType bodyType, PreferredFit preferredFit) {
        return ChildSizeInput.of(heightCm, weightKg, Gender.MALE, YearMonth.of(2019, 1), bodyType, preferredFit);
    }

    public static ChildSizeInput childSizeInputWithHeight(double heightCm) {
        return ChildSizeInput.of(heightCm, 20.0, Gender.MALE,
                YearMonth.of(2019, 1), BodyType.NORMAL, PreferredFit.LOOSE);
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
