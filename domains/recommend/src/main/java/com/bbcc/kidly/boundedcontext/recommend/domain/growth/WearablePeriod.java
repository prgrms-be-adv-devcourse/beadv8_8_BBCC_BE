package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

/**
 * 사이즈 하나를 앞으로 몇 개월 입을 수 있는지다. 계산할 수 없는 칸은 null이다.
 *
 * @param size 사이즈 이름
 * @param lengthMonths 길이 기준 개월 수. 권장 키 상한까지 크는 데 걸리는 기간이다
 * @param widthMonths 너비 기준 개월 수. 기준 옷보다 큰 너비를 몸이 채우는 데 걸리는 기간이다
 * @param months 길이 기준과 너비 기준 중 짧은 개월 수
 */
public record WearablePeriod(
        String size,
        Integer lengthMonths,
        Integer widthMonths,
        Integer months
) {
}
