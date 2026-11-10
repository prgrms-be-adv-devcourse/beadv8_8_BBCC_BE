package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

/**
 * 함께 사면 좋은 상품의 후보 점수에 쓰는 숫자를 모아 둔다. 가중치를 바꿀 때는 이 파일만 고친다.
 * 점수는 보고 있는 상품과의 유사도와 짝 종류의 우선순위 점수를 가중치로 더한 값이고, 가중치의 합은 1이다.
 */
public final class CompanionWeights {

    public static final double SIMILARITY = 0.3;

    public static final double PAIR_PRIORITY = 0.7;

    private CompanionWeights() {
    }
}
