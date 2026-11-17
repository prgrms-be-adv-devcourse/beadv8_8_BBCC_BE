package com.bbcc.kidly.boundedcontext.recommend.in.growth.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.dto.GrowthSpeedResult;
import java.math.BigDecimal;

/**
 * 자녀의 키 성장 속도다. 사이즈 추천 응답의 growthSpeed와 칸과 값이 같다.
 * 숫자는 소수 둘째 자리 반올림이고, 최근 속도는 기록이 부족하면 null이다.
 */
public record GrowthSpeedResponse(
    BigDecimal cmPerMonth,
    BigDecimal recentCmPerMonth,
    int recordCount,
    BigDecimal recordMonths,
    int childSharePercent,
    BigDecimal tableCmPerMonth,
    String tableSource) {

    public static GrowthSpeedResponse from(GrowthSpeedResult result) {
        return new GrowthSpeedResponse(result.cmPerMonth(), result.recentCmPerMonth(), result.recordCount(),
            result.recordMonths(), result.childSharePercent(), result.tableCmPerMonth(), result.tableSource());
    }
}
