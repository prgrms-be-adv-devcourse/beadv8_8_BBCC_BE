package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GarmentPartTest {

    @ParameterizedTest
    @CsvSource({"TOP, TOP", "BOTTOM, BOTTOM", "OUTER, OUTER", "DRESS, DRESS", "SKIRT, SKIRT"})
    @DisplayName("칸 이름 5개로 그 칸을 찾는다")
    void findPartByName(String name, GarmentPart expected) {
        assertThat(GarmentPart.from(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"top", "Bottom", "dress", "SHOES", "PANTS", " TOP"})
    @DisplayName("TOP · BOTTOM · OUTER · DRESS · SKIRT와 정확히 같지 않은 이름은 알 수 없는 구분으로 거절한다")
    void rejectUnknownName(String name) {
        assertThatThrownBy(() -> GarmentPart.from(name))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReferenceGarmentErrorCode.UNKNOWN_PART));
    }

    // 상품 카테고리 「하의」를 「바지」로 바꾸는 중이라 두 이름 모두 바지 칸이다
    @ParameterizedTest
    @CsvSource({"상의, TOP", "하의, BOTTOM", "바지, BOTTOM", "아우터, OUTER", "원피스, DRESS", "치마, SKIRT"})
    @DisplayName("상품 카테고리 경로로 기준 옷 칸을 찾는다")
    void findPartByCategoryPath(String leafName, GarmentPart expected) {
        assertThat(GarmentPart.ofCategoryPath(List.of("의류", leafName))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"모자", "양말"})
    @DisplayName("모자 · 양말처럼 어느 기준 옷 칸에도 들지 않는 카테고리는 칸이 없다")
    void noPartForAccessories(String leafName) {
        assertThat(GarmentPart.ofCategoryPath(List.of("잡화", leafName))).isNull();
    }
}
