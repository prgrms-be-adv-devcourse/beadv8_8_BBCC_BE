package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReferenceGarmentTest {

    private static final Long CHILD_ID = 11L;

    private static final Long TOP_CATEGORY_ID = 52L;

    private static final Long BOTTOM_CATEGORY_ID = 60L;

    @Test
    @DisplayName("기준 옷은 자녀 ID와 검증된 값의 구분 · 세부 종류 · 실측을 그대로 담는다")
    void createKeepsSpecValues() {
        ReferenceGarmentSpec spec = topSpec(TOP_CATEGORY_ID, "40.5", "42.0");

        ReferenceGarment garment = ReferenceGarment.create(CHILD_ID, spec);

        assertThat(garment.getChildProfileId()).isEqualTo(CHILD_ID);
        assertThat(garment.getPart()).isEqualTo(GarmentPart.TOP);
        assertThat(garment.getCategoryId()).isEqualTo(TOP_CATEGORY_ID);
        assertThat(garment.getMeasurements()).isEqualTo(spec.measurements());
    }

    @Test
    @DisplayName("같은 구분의 새 값으로 바꾸면 세부 종류와 실측이 바뀐다")
    void replaceChangesCategoryAndMeasurements() {
        ReferenceGarment garment = ReferenceGarment.create(CHILD_ID, topSpec(TOP_CATEGORY_ID, "40.5", "42.0"));
        ReferenceGarmentSpec replacement = topSpec(53L, "44.0", "45.5");

        garment.replace(replacement);

        assertThat(garment.getPart()).isEqualTo(GarmentPart.TOP);
        assertThat(garment.getCategoryId()).isEqualTo(53L);
        assertThat(garment.getMeasurements()).isEqualTo(replacement.measurements());
    }

    @Test
    @DisplayName("다른 구분의 값으로는 바꿀 수 없다")
    void replaceRejectsOtherPart() {
        ReferenceGarment garment = ReferenceGarment.create(CHILD_ID, topSpec(TOP_CATEGORY_ID, "40.5", "42.0"));
        ReferenceGarmentSpec bottom = bottomSpec();

        assertThatThrownBy(() -> garment.replace(bottom)).isInstanceOf(IllegalArgumentException.class);
        assertThat(garment.getPart()).isEqualTo(GarmentPart.TOP);
        assertThat(garment.getCategoryId()).isEqualTo(TOP_CATEGORY_ID);
    }

    @Test
    @DisplayName("자녀 ID나 값이 없으면 기준 옷을 만들 수 없다")
    void createRejectsNull() {
        ReferenceGarmentSpec spec = topSpec(TOP_CATEGORY_ID, "40.5", "42.0");

        assertThatThrownBy(() -> ReferenceGarment.create(null, spec)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ReferenceGarment.create(CHILD_ID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("바꿀 값이 없으면 예외이고 기준 옷은 그대로다")
    void replaceRejectsNull() {
        ReferenceGarment garment = ReferenceGarment.create(CHILD_ID, topSpec(TOP_CATEGORY_ID, "40.5", "42.0"));

        assertThatThrownBy(() -> garment.replace(null)).isInstanceOf(NullPointerException.class);
        assertThat(garment.getCategoryId()).isEqualTo(TOP_CATEGORY_ID);
    }

    private static ReferenceGarmentSpec topSpec(Long categoryId, String totalLength, String chestWidth) {
        return ReferenceGarmentSpec.of(GarmentPart.TOP, categoryId,
            List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(categoryId, "상의")),
            Map.of("TOTAL_LENGTH", new BigDecimal(totalLength), "CHEST_WIDTH", new BigDecimal(chestWidth)));
    }

    private static ReferenceGarmentSpec bottomSpec() {
        return ReferenceGarmentSpec.of(GarmentPart.BOTTOM, BOTTOM_CATEGORY_ID,
            List.of(new GarmentCategoryPathEntry(1L, "의류"),
                new GarmentCategoryPathEntry(BOTTOM_CATEGORY_ID, "하의")),
            Map.of("TOTAL_LENGTH", new BigDecimal("60.0"), "WAIST_WIDTH", new BigDecimal("30.0")));
    }
}
