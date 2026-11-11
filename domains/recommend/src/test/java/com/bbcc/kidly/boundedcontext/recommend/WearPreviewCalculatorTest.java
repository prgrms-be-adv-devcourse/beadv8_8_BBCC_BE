package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct.SizeMeasurements;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreviewCalculator;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.GrowthTableConfig;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WearPreviewCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    // 오늘 만 6세(78개월)인 여자아이다
    private static final LocalDate BIRTH_DATE_AGE_6 = LocalDate.of(2020, 5, 1);
    private static final ChildSizeInput GIRL_120 = ChildSizeInput.of(120.0, 21.0, Gender.FEMALE, BIRTH_DATE_AGE_6,
        BodyType.NORMAL, PreferredFit.REGULAR);
    private static final List<String> TOP_PATH = List.of("의류", "상의");

    private final WearPreviewCalculator calculator =
        new WearPreviewCalculator(new GrowthTableConfig().bodyHeightTable());

    @Test
    @DisplayName("PRV-01 몸 높이는 자녀 키 × 나이 · 성별 비율을 소수 첫째 자리로 반올림한 cm다")
    void bodyHeightsFromHeightAndRatio() {
        // 만 6세 여 비율: 목뒤 0.828 · 어깨 0.775 · 허리 0.605 · 엉덩이 0.481 · 샅 0.437 · 무릎 0.255 · 가쪽복사 0.043 · 팔 0.326
        WearPreview preview = calculator.preview(product(TOP_PATH, size("120", "45.0", null)), GIRL_120, TODAY)
            .orElseThrow();

        assertThat(preview.body()).isEqualTo(new WearPreview.Body(99.4, 93.0, 72.6, 57.7, 52.4, 30.6, 5.2, 39.1));
    }

    // 목뒤 99.36cm · 허리 72.6cm에서 총장을 뺀다
    @ParameterizedTest(name = "{0} 총장 {1}cm → {2}, 밑단 {3}cm")
    @CsvSource({
        "상의, 45.0, TOP, 54.4",
        "아우터, 50.0, OUTER, 49.4",
        "원피스, 60.0, DRESS, 39.4",
        "바지, 70.0, BOTTOM, 2.6",
        "치마, 30.0, SKIRT, 42.6",
    })
    @DisplayName("PRV-02 밑단 높이는 상의 · 아우터 · 원피스면 목뒤높이 − 총장, 바지 · 치마면 허리높이 − 총장이다")
    void hemHeightByGarment(String leafCategory, String totalLengthCm, GarmentPart expectedGarment,
        double expectedHemCm) {
        FitProduct product = product(List.of("의류", leafCategory), size("120", totalLengthCm, null));

        WearPreview preview = calculator.preview(product, GIRL_120, TODAY).orElseThrow();

        assertThat(preview.garment()).isEqualTo(expectedGarment);
        assertThat(preview.sizes()).extracting(WearPreview.SizeWear::size, WearPreview.SizeWear::hemCm)
            .containsExactly(tuple("120", expectedHemCm));
    }

    private static FitProduct product(List<String> categoryPathNames, SizeMeasurements... sizes) {
        return new FitProduct(1L, categoryPathNames, false, List.of(sizes));
    }

    // 총장 · 소매길이가 null이면 그 실측이 없는 사이즈다
    private static SizeMeasurements size(String size, String totalLengthCm, String sleeveLengthCm) {
        Map<String, BigDecimal> measurements = new HashMap<>();
        if (totalLengthCm != null) {
            measurements.put("TOTAL_LENGTH", new BigDecimal(totalLengthCm));
        }
        if (sleeveLengthCm != null) {
            measurements.put("SLEEVE_LENGTH", new BigDecimal(sleeveLengthCm));
        }
        return new SizeMeasurements(size, measurements);
    }
}
