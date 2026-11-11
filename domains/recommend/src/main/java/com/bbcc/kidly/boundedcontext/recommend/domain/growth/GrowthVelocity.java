package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

/**
 * 아이의 키 성장 속도다.
 *
 * @param cmPerMonth 전체 속도(cm/월)
 * @param recentCmPerMonth 최근 3개월 속도(cm/월). 계산할 수 없으면 null이다
 * @param childCmPerMonth 표와 섞기 전 아이 기록만의 최소제곱 기울기(cm/월). 표 값만 쓸 때는 null이다
 * @param recordCount 계산에 쓴 기록 개수
 * @param recordMonths 첫 기록과 마지막 기록 사이 개월 수
 * @param childShare 전체 속도에서 아이 기록이 차지하는 비중(0 ~ 1)
 * @param tableCmPerMonth 표 값(cm/월)
 * @param tableSource 표 출처
 */
public record GrowthVelocity(
        double cmPerMonth,
        Double recentCmPerMonth,
        Double childCmPerMonth,
        int recordCount,
        double recordMonths,
        double childShare,
        double tableCmPerMonth,
        String tableSource
) {
}
