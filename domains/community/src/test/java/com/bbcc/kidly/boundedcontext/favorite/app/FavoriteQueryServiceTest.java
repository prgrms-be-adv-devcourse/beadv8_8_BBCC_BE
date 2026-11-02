package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FavoriteQueryServiceTest {

    @Test
    @DisplayName("productIds: 쉼표 구분 양의 정수 1~50개(앞뒤 공백 허용, 중복은 하나로)")
    void productIdsAccepted() {
        assertThat(FavoriteQueryService.parseProductIds("1, 2,3,2")).containsExactly(1L, 2L, 3L);
        assertThat(FavoriteQueryService.parseProductIds(ids(50))).hasSize(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1,,2", "a,1", "0", "-1", "1.5"})
    @DisplayName("productIds 형식이 틀리면 INVALID_PRODUCT_IDS")
    void productIdsInvalidFormat(String productIds) {
        assertInvalid(productIds);
    }

    @Test
    @DisplayName("productIds가 51개면 INVALID_PRODUCT_IDS")
    void tooManyProductIds() {
        assertInvalid(ids(51));
    }

    @Test
    @DisplayName("productIds는 중복을 합치기 전 개수로 센다: 같은 ID 51개도 INVALID_PRODUCT_IDS")
    void duplicatesCountedBeforeMerge() {
        assertInvalid(String.join(",", Collections.nCopies(51, "7")));
    }

    private static void assertInvalid(String productIds) {
        assertThatThrownBy(() -> FavoriteQueryService.parseProductIds(productIds))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(FavoriteErrorCode.INVALID_PRODUCT_IDS));
    }

    private static String ids(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }
}
