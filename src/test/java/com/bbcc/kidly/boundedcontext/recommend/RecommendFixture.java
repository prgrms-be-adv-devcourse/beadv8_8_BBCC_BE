package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
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

    /**
     * 기획서 7.1 자녀 1의 ID. 회원 ID와 값이 같으면 두 칸을 바꿔 넣어도 테스트가 통과하므로 다른 값을 쓴다.
     */
    public static final Long CHILD_1_ID = 11L;

    public static final Long MEMBER_1_ID = 1L;

    public static final Long OTHER_MEMBER_ID = 2L;

    /**
     * 자녀 정보 변경 이벤트가 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    public static final LocalDateTime CHILD_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    /**
     * 아래 네 상수는 구매확정에 쓰는 주문 · 주문상품 · 상품 · 옵션 ID다. ID마다 다른 값을 써서 두 칸을 바꿔 넣으면 테스트가 실패하게 한다.
     */
    public static final Long ORDER_ID = 100L;

    public static final Long ORDER_ITEM_ID = 101L;

    public static final Long PRODUCT_A_ID = 31L;

    public static final Long PRODUCT_A_120_OPTION_ID = 32L;

    public static final LocalDateTime PURCHASE_CONFIRMED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    private RecommendFixture() {
    }

    /**
     * 회원 1이 자녀 1을 골라 주문한 상품 A의 120 옵션을 구매확정했다.
     */
    public static PurchaseRecord.RecordInfo purchaseConfirmation() {
        return purchaseConfirmationFor(CHILD_1_ID);
    }

    /**
     * {@link #purchaseConfirmation()}과 같은 구매확정에서 자녀만 바꾼다. 자녀를 고르지 않았으면 null이다.
     */
    public static PurchaseRecord.RecordInfo purchaseConfirmationFor(Long childProfileId) {
        return new PurchaseRecord.RecordInfo(ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID,
            PRODUCT_A_120_OPTION_ID, "120", PURCHASE_CONFIRMED_AT);
    }

    /**
     * 기획서 7.1 자녀 1의 자녀 정보 변경: 회원 1의 자녀, 2019-01-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static RecommendReplicaChild.ChangeInfo childChange() {
        return childChange(BodyType.NORMAL, PreferredFit.LOOSE);
    }

    public static RecommendReplicaChild.ChangeInfo childChange(BodyType bodyType, PreferredFit preferredFit) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("115.0"),
            new BigDecimal("20.0"), Gender.MALE, CHILD_1_BIRTH_DATE, bodyType, preferredFit, false, CHILD_CHANGED_AT);
    }

    /**
     * 자녀 1의 키만 바꾼 자녀 정보 변경. 어느 이벤트가 반영됐는지 키로 구분한다.
     */
    public static RecommendReplicaChild.ChangeInfo childChangeAt(BigDecimal heightCm, LocalDateTime occurredAt) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, heightCm, new BigDecimal("20.0"),
            Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false, occurredAt);
    }

    /**
     * 자녀 1의 삭제. 삭제 이벤트도 삭제 직전 값(키 115.0cm 등)을 그대로 담는다.
     */
    public static RecommendReplicaChild.ChangeInfo childDeleted(LocalDateTime occurredAt) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("115.0"),
            new BigDecimal("20.0"), Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, true,
            occurredAt);
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
