package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.bbcc.kidly.boundedcontext.search.domain.SearchSeller;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 판매자 변경됨에서 옮긴 상호명으로 검색 판매자를 만든다.
 */
class SearchSellerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    @Test
    @DisplayName("판매자 ID · 상호명 · 이벤트 발생 시각을 받은 값 그대로 옮긴다")
    void copiesValues() {
        SearchSeller seller = SearchSeller.create(new SearchSeller.ChangeInfo(7L, "키들리 상점", OCCURRED_AT));

        assertThat(seller)
            .extracting(SearchSeller::getSellerId, SearchSeller::getBusinessName, SearchSeller::getOccurredAt)
            .containsExactly(7L, "키들리 상점", OCCURRED_AT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changeInfosMissingRequiredValue")
    @DisplayName("필수 값 중 하나라도 없으면 만들지 않는다")
    void rejectsMissingRequiredValue(String field, SearchSeller.ChangeInfo info) {
        assertThatNullPointerException()
            .isThrownBy(() -> SearchSeller.create(info))
            .withMessageStartingWith(field);
    }

    static Stream<Arguments> changeInfosMissingRequiredValue() {
        return Stream.of(
            arguments("info", null),
            arguments("sellerId", new SearchSeller.ChangeInfo(null, "키들리 상점", OCCURRED_AT)),
            arguments("businessName", new SearchSeller.ChangeInfo(7L, null, OCCURRED_AT)),
            arguments("occurredAt", new SearchSeller.ChangeInfo(7L, "키들리 상점", null)));
    }
}
