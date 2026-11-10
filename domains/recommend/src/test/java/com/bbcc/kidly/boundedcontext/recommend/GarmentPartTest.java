package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GarmentPartTest {

    @ParameterizedTest
    @CsvSource({"TOP, TOP", "BOTTOM, BOTTOM"})
    @DisplayName("구분 이름으로 그 구분을 찾는다")
    void findPartByName(String name, GarmentPart expected) {
        assertThat(GarmentPart.from(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"top", "Bottom", "SHOES", " TOP"})
    @DisplayName("TOP이나 BOTTOM과 정확히 같지 않은 이름은 알 수 없는 구분으로 거절한다")
    void rejectUnknownName(String name) {
        assertThatThrownBy(() -> GarmentPart.from(name))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReferenceGarmentErrorCode.UNKNOWN_PART));
    }
}
