package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SizeNoticeTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "BODY_TYPE_ADJUSTED | 입력하신 체형보다 키와 몸무게를 우선해 추천했어요.",
        "HEIGHT_ONLY | 입력하신 체형이 키·몸무게와 많이 달라 키만 기준으로 추천했어요.",
        "PREFERRED_FIT_NOT_APPLIED | 아이 체형을 먼저 고려해 선호 핏과 다른 사이즈를 추천했어요.",
        "NO_MATCHING_SIZE | 아이 키에 맞는 사이즈가 없어요.",
        "SIZE_FREE | 사이즈 구분이 없는 상품이에요."})
    @DisplayName("INV-R05 안내마다 화면에 보여 줄 문구가 있고, 문구에 체형 판정값(마름 · 보통 · 통통)이 없다")
    void messageOfEachNotice(SizeNotice notice, String message) {
        assertThat(notice.getMessage()).isEqualTo(message);
        assertThat(notice.getMessage()).doesNotContain("마름", "보통", "통통");
    }
}
