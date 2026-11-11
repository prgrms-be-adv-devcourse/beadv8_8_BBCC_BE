package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI가 구조화 출력으로 돌려준 사이즈 답이다.
 * 모델이 칸을 비우거나 모르는 값을 줄 수 있어 문자열로 받고, 규칙 결과와 합칠 때 검사한다.
 * itemJudgements가 null이면 빈 목록으로 다룬다.
 */
public record AiSizeAnswer(String recommendedSize, String fit, List<Reason> reasons,
                           List<Judgement> itemJudgements) {

    public AiSizeAnswer {
        if (itemJudgements == null) {
            itemJudgements = List.of();
        }
    }

    public record Reason(String type, String detail, String sourceId) {
    }

    /**
     * 실측 항목 하나에 대한 AI의 판단이다. 모델이 값을 비우거나 모르는 값을 줄 수 있어 판단은 문자열로 받는다.
     */
    public record Judgement(String code, BigDecimal diffCm, String fit, BigDecimal allowanceMinCm,
                            BigDecimal allowanceMaxCm) {
    }
}
