package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.util.List;

/**
 * AI가 구조화 출력으로 돌려준 사이즈 답이다.
 * 모델이 칸을 비우거나 모르는 값을 줄 수 있어 문자열로 받고, 규칙 결과와 합칠 때 검사한다.
 */
public record AiSizeAnswer(String recommendedSize, String fit, List<Reason> reasons) {

    public record Reason(String type, String detail, String sourceId) {
    }
}
