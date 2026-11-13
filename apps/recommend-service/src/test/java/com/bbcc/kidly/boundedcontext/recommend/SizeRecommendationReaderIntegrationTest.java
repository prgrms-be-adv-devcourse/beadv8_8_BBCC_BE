package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사이즈 추천의 AI 입력을 확인한다.
 * 자녀 1은 2019-01-01생 남아(키 115cm, 몸무게 20kg, 체형 보통, 선호 핏 여유)이고, 상품 A는 110(105~115cm) · 120(115~125cm)이다.
 * 상품 A의 이벤트 fixture에는 실측이 없어서, 실측이 있는 복사본을 리포지토리로 바로 저장한다.
 */
@IntegrationTest
class SizeRecommendationReaderIntegrationTest {

    // 나이에 따라 표 값이 바뀌므로 실행하는 날과 상관없이 늘 만 7세가 되게 한다(만 7세 남아 6.00cm/년)
    private static final LocalDate SEVEN_AND_A_HALF_YEARS_AGO =
        LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(7).minusMonths(6);

    @Autowired
    private SizeRecommendationReader reader;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private RecommendGrowthRecordRepository growthRecordRepository;

    @Test
    @DisplayName("AI 입력에 자녀 개월 수 · 키 · 몸무게 · 체형 · 선호 핏 · 판정 체형, 사이즈별 권장 키와 실측, 규칙 결과를 담는다")
    void buildAiQuestion() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        SizeAiQuestion question = draft.aiQuestion();
        assertThat(question.child()).isEqualTo(new SizeAiQuestion.Child(
            ageMonthsToday(), 115.0, 20.0, BodyType.NORMAL, PreferredFit.LOOSE, BodyType.NORMAL));
        assertThat(question.sizes()).containsExactlyInAnyOrder(
            new SizeAiQuestion.SizeLine("110", 105, 115,
                Map.of("TOTAL_LENGTH", new BigDecimal("42"), "CHEST_WIDTH", new BigDecimal("34"))),
            new SizeAiQuestion.SizeLine("120", 115, 125,
                Map.of("TOTAL_LENGTH", new BigDecimal("45"), "CHEST_WIDTH", new BigDecimal("36"))));
        assertThat(question.ruleResult())
            .isEqualTo(new SizeAiQuestion.RuleResult("120", "ROOMY", List.of("110", "120"), List.of()));
        assertThat(question.referenceFit()).isNull();
    }

    @Test
    @DisplayName("FIT-11 지난 AI 답을 찾는 키는 상품 · 자녀 · 개월 수 · 키 · 몸무게 · 체형 · 선호 핏 · 프롬프트 판 6으로 만든다")
    void buildAiKeyInput() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        AiResponseKey.Input keyInput = draft.aiKeyInput();
        assertThat(keyInput.productId()).isEqualTo(PRODUCT_A_ID);
        assertThat(keyInput.childProfileId()).isEqualTo(CHILD_1_ID);
        assertThat(keyInput.ageMonths()).isEqualTo(ageMonthsToday());
        assertThat(keyInput.heightCm()).isEqualByComparingTo("115");
        assertThat(keyInput.weightKg()).isEqualByComparingTo("20");
        assertThat(keyInput.bodyType()).isEqualTo(BodyType.NORMAL);
        assertThat(keyInput.preferredFit()).isEqualTo(PreferredFit.LOOSE);
        assertThat(keyInput.candidateProductIds()).isNull();
        assertThat(keyInput.cartProductIds()).isNull();
        assertThat(keyInput.promptVersion()).isEqualTo(6);
    }

    @Test
    @DisplayName("FIT-01 · FIT-06 자녀의 기준 옷이 있으면 사려는 상품과 비교한 기준 핏을 AI 입력과 답 검증 값에 담는다")
    void buildReferenceFit() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        // 상의 기준 옷: 총장 40.5cm, 가슴단면 42.0cm
        referenceGarmentRepository.save(ReferenceGarmentFixture.top(CHILD_1_ID));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        ReferenceFit expected = new ReferenceFit(GarmentPart.TOP, GarmentPart.TOP, List.of(
            new ReferenceFit.Item("TOTAL_LENGTH", new BigDecimal("40.5"), List.of(
                new ReferenceFit.SizeDiff("110", new BigDecimal("42"), new BigDecimal("1.5")),
                new ReferenceFit.SizeDiff("120", new BigDecimal("45"), new BigDecimal("4.5")))),
            new ReferenceFit.Item("CHEST_WIDTH", new BigDecimal("42.0"), List.of(
                new ReferenceFit.SizeDiff("110", new BigDecimal("34"), new BigDecimal("-8.0")),
                new ReferenceFit.SizeDiff("120", new BigDecimal("36"), new BigDecimal("-6.0"))))));
        assertThat(draft.aiQuestion().referenceFit())
            .usingRecursiveComparison(cmComparison()).isEqualTo(expected);
        assertThat(draft.aiEvidence().referenceFit())
            .usingRecursiveComparison(cmComparison()).isEqualTo(expected);
    }

    @Test
    @DisplayName("FIT-10 답 검증 값에는 AI 입력에 들어간 자녀 · 사이즈 · 실측 · 기준 핏의 숫자를 모은다")
    void collectGivenNumbers() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        referenceGarmentRepository.save(ReferenceGarmentFixture.top(CHILD_1_ID));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        List<String> given = new ArrayList<>();
        for (BigDecimal number : draft.aiEvidence().givenNumbers()) {
            given.add(number.stripTrailingZeros().toPlainString());
        }
        assertThat(given).contains(
            String.valueOf(ageMonthsToday()), "115", "20",
            "110", "120", "105", "125",
            "42", "34", "45", "36",
            "40.5", "1.5", "4.5", "-8", "-6");
    }

    @Test
    @DisplayName("기준 핏이 달라지면 지난 AI 답을 찾는 키도 달라져, 기준 옷을 바꾼 뒤 예전 답을 다시 쓰지 않는다")
    void aiKeyChangesWithReferenceFit() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        AiResponseKey withoutGarment =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        referenceGarmentRepository.save(ReferenceGarmentFixture.top(CHILD_1_ID));
        AiResponseKey withGarment =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        assertThat(withGarment).isNotEqualTo(withoutGarment);
    }

    @Test
    @DisplayName("GROW-01 · GROW-02 성장 기록이 없으면 AI 입력의 성장 속도는 나이 · 성별 표 값(만 7세 남아 6.00cm/년 ÷ 12)이고 아이 기록 비중은 0이다")
    void growthSpeedFromTableWithoutRecords() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEventBornOn(SEVEN_AND_A_HALF_YEARS_AGO));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        assertThat(draft.aiQuestion().growthSpeed())
            .usingRecursiveComparison(cmComparison())
            .isEqualTo(new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.50"), null, 0, new BigDecimal("0"), 0,
                new BigDecimal("0.50"), "한국아동패널"));
    }

    @Test
    @DisplayName("GROW-03 최근 12개월 안 성장 기록이 둘 이상이고 한 달 넘게 떨어져 있으면 아이 기록을 표 값과 섞고, 12개월보다 오래된 기록은 쓰지 않는다")
    void growthSpeedBlendsChildRecords() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        LocalDateTime now = LocalDate.now(ZoneId.of("Asia/Seoul")).atTime(9, 0);
        // 6개월에 4cm(약 0.66cm/월)라 표 값 0.50cm/월과 다르다
        saveGrowthRecord(1L, "108.0", now.minusDays(400));
        saveGrowthRecord(2L, "111.0", now.minusDays(182));
        saveGrowthRecord(3L, "115.0", now.minusDays(1));

        SizeAiQuestion.GrowthSpeed growthSpeed = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID)
            .aiQuestion().growthSpeed();

        assertThat(growthSpeed.recordCount()).isEqualTo(2);
        assertThat(growthSpeed.childSharePercent()).isBetween(1, 99);
        assertThat(growthSpeed.cmPerMonth()).isNotEqualByComparingTo(growthSpeed.tableCmPerMonth());
    }

    // 자녀 1은 키 115cm이고 기록이 없어 표 값 0.50cm/월로 큰다. 기준 옷이 없어 너비 기준은 계산하지 않는다
    @Test
    @DisplayName("WEAR-01 · WEAR-03 사이즈마다 권장 키 상한까지 크는 개월 수를 입을 수 있는 기간으로 AI 입력에 담는다")
    void buildWearablePeriods() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEventBornOn(SEVEN_AND_A_HALF_YEARS_AGO));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        assertThat(draft.aiQuestion().wearablePeriods()).containsExactly(
            new WearablePeriod("110", 0, null, 0),
            new WearablePeriod("120", 20, null, 20));
    }

    // 상의 기준 옷 가슴단면 35cm보다 상품 120의 가슴단면(36cm)이 1cm 넉넉하다
    @Test
    @DisplayName("WEAR-02 · WEAR-03 기준 옷이 있으면 너비 기준 개월 수도 계산해 길이 기준과 중 짧은 쪽을 입을 수 있는 기간으로 담는다")
    void buildWearablePeriodsWithWidth() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEventBornOn(SEVEN_AND_A_HALF_YEARS_AGO));
        referenceGarmentRepository.save(ReferenceGarment.create(CHILD_1_ID, ReferenceGarmentSpec.of(GarmentPart.TOP,
            52L, List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(52L, "상의")),
            Map.of("TOTAL_LENGTH", new BigDecimal("40.0"), "CHEST_WIDTH", new BigDecimal("35.0")))));

        WearablePeriod size120 = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiQuestion().wearablePeriods()
            .get(1);

        assertThat(size120.size()).isEqualTo("120");
        assertThat(size120.widthMonths()).isNotNull();
        assertThat(size120.months()).isEqualTo(Math.min(size120.lengthMonths(), size120.widthMonths()));
    }

    @Test
    @DisplayName("WEAR-04 사이즈 무관 상품은 입을 수 있는 기간을 계산하지 않아 빈 목록이다")
    void noWearablePeriodsForSizeFreeProduct() {
        replicaProductRepository.save(RecommendFixture.productASizeFreeReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        assertThat(draft.aiQuestion().wearablePeriods()).isEmpty();
    }

    @Test
    @DisplayName("FIT-11 성장 기록이 늘어 성장 속도가 달라지면 지난 AI 답을 찾는 키도 달라져, 예전 답을 다시 쓰지 않는다")
    void aiKeyChangesWithGrowthSpeed() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        AiResponseKey withoutRecords =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        LocalDateTime now = LocalDate.now(ZoneId.of("Asia/Seoul")).atTime(9, 0);
        saveGrowthRecord(1L, "111.0", now.minusDays(182));
        saveGrowthRecord(2L, "115.0", now.minusDays(1));
        AiResponseKey withRecords =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        assertThat(withRecords).isNotEqualTo(withoutRecords);
    }

    // 기록이 없어 성장 속도는 표 값 0.50cm/월이고, 사이즈 120은 20개월 입을 수 있다
    @Test
    @DisplayName("FIT-10 답 검증 값에는 AI 입력에 들어간 성장 속도 · 입을 수 있는 개월 수와 지시에 나오는 기간(최근 3개월 · 12개월 · 상한 24개월)도 모은다")
    void collectGrowthNumbers() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEventBornOn(SEVEN_AND_A_HALF_YEARS_AGO));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        List<String> given = new ArrayList<>();
        for (BigDecimal number : draft.aiEvidence().givenNumbers()) {
            given.add(number.stripTrailingZeros().toPlainString());
        }
        assertThat(given).contains("0.5", "20", "3", "12", "24");
    }

    @Test
    @DisplayName("AI 입력에는 자녀 이름 · 생년월일 · 회원 ID를 담는 칸이 없다")
    void aiQuestionHasNoPersonalFields() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : List.of(SizeAiQuestion.class, SizeAiQuestion.Child.class,
            SizeAiQuestion.GrowthSpeed.class, WearablePeriod.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
            }
        }

        assertThat(names).doesNotContain("name", "birthDate", "memberId", "childProfileId");
    }

    private void saveGrowthRecord(Long growthRecordId, String heightCm, LocalDateTime recordedAt) {
        growthRecordRepository.save(RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
            growthRecordId, CHILD_1_ID, new BigDecimal(heightCm), null, recordedAt)));
    }

    // 1.5와 1.50처럼 소수 자리만 다른 값은 같은 cm로 본다
    private static RecursiveComparisonConfiguration cmComparison() {
        return RecursiveComparisonConfiguration.builder()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .build();
    }

    private static int ageMonthsToday() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return (int) Period.between(LocalDate.of(2019, 1, 1), today).toTotalMonths();
    }
}
