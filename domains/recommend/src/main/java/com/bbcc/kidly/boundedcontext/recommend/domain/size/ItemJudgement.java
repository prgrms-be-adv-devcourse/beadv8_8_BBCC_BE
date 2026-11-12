package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.math.BigDecimal;

/**
 * 추천 사이즈의 실측 항목 하나에 대한 판단이다. 항목 코드, 기준 옷과의 차이(cm), 판단, 판단의 근거가 된 여유 범위(cm)를 담는다.
 */
public record ItemJudgement(String code, BigDecimal diffCm, Fit fit, BigDecimal allowanceMinCm,
                            BigDecimal allowanceMaxCm) {
}
