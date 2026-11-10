package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CompanionCandidateTest {

    private static final LocalDateTime SALE_STARTED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);
    private static final List<String> BOTTOM = List.of("의류", "하의");

    static Stream<Arguments> missingRequiredValues() {
        return Stream.of(
            Arguments.of(null, BOTTOM),
            Arguments.of(product(1L, SALE_STARTED_AT), null),
            Arguments.of(product(null, SALE_STARTED_AT), BOTTOM),
            Arguments.of(product(1L, null), BOTTOM));
    }

    @ParameterizedTest
    @MethodSource("missingRequiredValues")
    @DisplayName("상품, 카테고리 경로, 상품 ID, 판매 시작 시각 중 하나라도 없으면 후보를 만들지 않는다")
    void rejectMissingRequiredValues(RecommendedProduct product, List<String> categoryPathNames) {
        assertThatThrownBy(() -> new CompanionCandidate(product, categoryPathNames, 0.5))
            .isInstanceOf(NullPointerException.class);
    }

    private static RecommendedProduct product(Long productId, LocalDateTime saleStartedAt) {
        return new RecommendedProduct(productId, 100L, "상품", null, 10_000L, "110", 1L, null, SALE_STARTED_AT,
            saleStartedAt);
    }
}
