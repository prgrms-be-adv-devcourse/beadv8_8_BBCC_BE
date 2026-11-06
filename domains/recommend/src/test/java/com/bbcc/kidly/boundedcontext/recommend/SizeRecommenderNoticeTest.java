package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.TODAY;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.childSizeInputWith;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.recommender;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeRecommenderNoticeTest {

    @ParameterizedTest(name = "몸무게 {0}kg, 입력 체형 {1} → 안내 {2}")
    @CsvSource({
        "30.0, NORMAL, true",
        "18.0, NORMAL, true",
        "20.0, SLIM, true",
        "20.0, CHUNKY, true",
        "20.0, NORMAL, false",
        "30.0, CHUNKY, false",
        "30.0, , false",
    })
    @DisplayName("판정 체형이 입력 체형과 한 단계 다르면 BODY_TYPE_ADJUSTED 안내를 준다 (같거나 입력이 없으면 주지 않는다)")
    void bodyTypeAdjustedNotice(double weightKg, BodyType inputBodyType, boolean expectedNotice) {
        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(115.0, weightKg, inputBodyType, PreferredFit.LOOSE), productA(), TODAY);

        assertThat(result.getNotices().contains(SizeNotice.BODY_TYPE_ADJUSTED)).isEqualTo(expectedNotice);
    }

    @ParameterizedTest(name = "사이즈 {0}~{1}cm, {2}~{3}cm → 안내 {4}")
    @CsvSource({
        "117, 125, , , false",
        "113, 125, , , true",
        "117, 125, 105, 113, true",
    })
    @DisplayName("후보가 범위 밖 하나뿐이라 체형을 쓰지 않았으면 BODY_TYPE_ADJUSTED 안내를 주지 않는다")
    void noBodyTypeNoticeWhenBodyTypeUnused(
        int minHeightCm, int maxHeightCm, Integer otherMinHeightCm, Integer otherMaxHeightCm, boolean expectedNotice) {
        List<SizeRange> sizeRanges = new ArrayList<>();
        sizeRanges.add(SizeRange.of("120", minHeightCm, maxHeightCm));
        if (otherMinHeightCm != null) {
            sizeRanges.add(SizeRange.of("110", otherMinHeightCm, otherMaxHeightCm));
        }

        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(115.0, 30.0, BodyType.NORMAL, PreferredFit.LOOSE),
            ProductSizes.of(false, sizeRanges), TODAY);

        assertThat(result.getNotices().contains(SizeNotice.BODY_TYPE_ADJUSTED)).isEqualTo(expectedNotice);
    }

    @ParameterizedTest(name = "키 {0}cm, 몸무게 {1}kg, 입력 체형 {2}, 선호 핏 {3} → 안내 {4}")
    @CsvSource({
        "115.0, 30.0, CHUNKY, SLIM, true",
        "115.0, 30.0, NORMAL, SLIM, true",
        "115.0, 18.0, NORMAL, LOOSE, true",
        "115.0, 18.0, SLIM, REGULAR, true",
        "115.0, 30.0, CHUNKY, LOOSE, false",
        "115.0, 18.0, SLIM, SLIM, false",
        "115.0, 30.0, CHUNKY, REGULAR, false",
        "115.0, 30.0, CHUNKY, , false",
        "115.0, 20.0, NORMAL, SLIM, false",
        "120.0, 30.0, CHUNKY, SLIM, false",
    })
    @DisplayName("체형으로 고른 사이즈가 선호 핏이 원한 사이즈와 다르면 PREFERRED_FIT_NOT_APPLIED 안내를 준다")
    void preferredFitNotAppliedNotice(
        double heightCm, double weightKg, BodyType inputBodyType, PreferredFit preferredFit, boolean expectedNotice) {
        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(heightCm, weightKg, inputBodyType, preferredFit), productA(), TODAY);

        assertThat(result.getNotices().contains(SizeNotice.PREFERRED_FIT_NOT_APPLIED)).isEqualTo(expectedNotice);
    }

    @ParameterizedTest(name = "키 {0}cm, 몸무게 {1}kg, 입력 체형 {2}, 선호 핏 {3} → 안내 [{4}]")
    @CsvSource({
        "115.0, 30.0, SLIM, LOOSE, HEIGHT_ONLY",
        "115.0, 18.0, CHUNKY, LOOSE, HEIGHT_ONLY",
        "115.0, 30.0, SLIM, SLIM, HEIGHT_ONLY",
        "115.0, 30.0, NORMAL, LOOSE, BODY_TYPE_ADJUSTED",
    })
    @DisplayName("입력 체형과 판정 체형이 두 단계 다르면 HEIGHT_ONLY 안내 하나만 준다")
    void heightOnlyNotice(
        double heightCm, double weightKg, BodyType inputBodyType, PreferredFit preferredFit, String expectedNotices) {
        SizeRecommendation result = recommender().recommend(
            childSizeInputWith(heightCm, weightKg, inputBodyType, preferredFit), productA(), TODAY);

        assertThat(result.getNotices()).isEqualTo(noticesOf(expectedNotices));
    }

    @ParameterizedTest(name = "생년월일 {0}")
    @CsvSource({
        "2007-10-12",
        "2024-10-13",
    })
    @DisplayName("규칙 14-3 228개월부터나 영아 기준표에 없는 누운키면 판정 없이 입력 체형으로 고르고 안내를 주지 않는다")
    void inputBodyTypeWithoutNoticeOutsideChart(LocalDate birthDate) {
        ChildSizeInput child = ChildSizeInput.of(
            115.0, 30.0, Gender.MALE, birthDate, BodyType.SLIM, PreferredFit.SLIM);

        SizeRecommendation result = recommender().recommend(child, productA(), TODAY);

        assertThat(result.getRecommendedSize()).isEqualTo("110");
        assertThat(result.getNotices()).isEmpty();
    }

    private List<SizeNotice> noticesOf(String names) {
        return Arrays.stream(names.split(" "))
            .filter(name -> !name.isBlank())
            .map(SizeNotice::valueOf)
            .toList();
    }
}
