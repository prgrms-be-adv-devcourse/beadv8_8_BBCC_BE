package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.util.Set;

/**
 * AI에게 준 질문에서 AI 답을 검증할 때 쓰는 값이다.
 * 기준 핏이 없으면 referenceFit은 null이다. givenNumbers는 질문에 실제로 담긴 숫자들이다.
 */
public record SizeAiEvidence(ReferenceFit referenceFit, Set<BigDecimal> givenNumbers) {
}
