package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AgeGroup;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChartRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.WeightPercentiles;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecommendFixture {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    /**
     * 기획서 7.1 자녀 1의 생년월일. 2026-10-12 기준 만 7세라 3~18세 표를 쓴다.
     */
    public static final LocalDate CHILD_1_BIRTH_DATE = LocalDate.of(2019, 1, 1);

    public static final Long CHILD_1_ID = 1L;

    public static final Long MEMBER_1_ID = 1L;

    /**
     * 자녀 정보 변경 이벤트가 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    public static final LocalDateTime CHILD_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    private RecommendFixture() {
    }

    /**
     * 기획서 7.1 자녀 1의 자녀 정보 변경: 회원 1의 자녀, 2019-01-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static ReplicaChild.ChangeInfo childChange() {
        return childChange(BodyType.NORMAL, PreferredFit.LOOSE);
    }

    public static ReplicaChild.ChangeInfo childChange(BodyType bodyType, PreferredFit preferredFit) {
        return new ReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("115.0"), new BigDecimal("20.0"),
            Gender.MALE, CHILD_1_BIRTH_DATE, bodyType, preferredFit, false, CHILD_CHANGED_AT);
    }

    /**
     * 자녀 1의 키만 바꾼 자녀 정보 변경. 어느 이벤트가 반영됐는지 키로 구분한다.
     */
    public static ReplicaChild.ChangeInfo childChange(BigDecimal heightCm, LocalDateTime occurredAt) {
        return new ReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, heightCm, new BigDecimal("20.0"),
            Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false, occurredAt);
    }

    public static SizeRecommender recommender() {
        return new SizeRecommender(growthChart());
    }

    /**
     * 테스트용 체형표. 질병관리청 2017 성장도표의 남자 115cm 행 두 개만 있다.
     * 3~18세 15백분위 19.1kg, 85백분위 22.7kg / 2~3세 미만 18.6kg, 22.4kg.
     * 그 밖은 표에 없어서, 계산은 보호자가 입력한 체형을 그대로 쓴다.
     */
    public static GrowthChart growthChart() {
        return GrowthChart.of(List.of(
            GrowthChartRow.of(AgeGroup.YEARS_3_18, Gender.MALE, 115.0, WeightPercentiles.of(19.1, 22.7)),
            GrowthChartRow.of(AgeGroup.MONTHS_24_35, Gender.MALE, 115.0, WeightPercentiles.of(18.6, 22.4))));
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

    public static ChildSizeInput childSizeInputBornOn(
        LocalDate birthDate, double weightKg, PreferredFit preferredFit) {
        return ChildSizeInput.of(115.0, weightKg, Gender.MALE, birthDate, BodyType.NORMAL, preferredFit);
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
