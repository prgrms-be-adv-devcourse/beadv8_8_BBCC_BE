package com.bbcc.kidly.boundedcontext.recommend.app.growth.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import java.math.BigDecimal;

/**
 * 자녀의 키 성장 속도다. 칸의 뜻과 반올림은 사이즈 추천 응답의 성장 속도와 같다.
 *
 * @param cmPerMonth 전체 성장 속도(cm/월)
 * @param recentCmPerMonth 최근 3개월 성장 속도(cm/월). 계산할 수 없으면 null이다
 * @param recordCount 계산에 쓴 성장 기록 개수
 * @param recordMonths 첫 기록과 마지막 기록 사이 개월 수
 * @param childSharePercent 전체 속도에서 자녀 기록이 차지하는 비중(0 ~ 100)
 * @param tableCmPerMonth 나이 · 성별 표 값(cm/월)
 * @param tableSource 표 출처
 */
public record GrowthSpeedResult(
    BigDecimal cmPerMonth,
    BigDecimal recentCmPerMonth,
    int recordCount,
    BigDecimal recordMonths,
    int childSharePercent,
    BigDecimal tableCmPerMonth,
    String tableSource) {

    /**
     * 사이즈 추천이 쓰는 변환을 그대로 써서 두 API의 숫자가 소수 자리까지 같게 한다.
     */
    public static GrowthSpeedResult from(GrowthVelocity velocity) {
        SizeAiQuestion.GrowthSpeed speed = SizeAiQuestion.GrowthSpeed.from(velocity);
        return new GrowthSpeedResult(speed.cmPerMonth(), speed.recentCmPerMonth(), speed.recordCount(),
            speed.recordMonths(), speed.childSharePercent(), speed.tableCmPerMonth(), speed.tableSource());
    }
}
