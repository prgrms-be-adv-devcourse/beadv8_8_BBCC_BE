package com.bbcc.kidly.boundedcontext.recommend.domain.size;

/**
 * 상품에 공개된 리뷰 핏이 작아요 · 잘 맞아요 · 커요로 몇 건씩인지를 사이즈 AI 입력으로 넘기는 값이다.
 */
public record ProductFitTendency(int smallCount, int trueToSizeCount, int largeCount) {

    // 공개된 핏 리뷰가 1 ~ 2건이면 한 사람의 의견이 상품의 성향처럼 보이므로 이 수보다 적으면 성향을 만들지 않는다
    static final int MIN_REVIEW_COUNT = 5;

    public static ProductFitTendency ofCounts(int small, int trueToSize, int large) {
        if (small + trueToSize + large < MIN_REVIEW_COUNT) {
            return null;
        }
        return new ProductFitTendency(small, trueToSize, large);
    }

    /**
     * 핏을 남긴 공개 리뷰의 전체 수다.
     */
    public int totalCount() {
        return smallCount + trueToSizeCount + largeCount;
    }
}
