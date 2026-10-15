package com.bbcc.kidly.boundedcontext.recommend.app.size.dto;

import java.util.List;

/**
 * 상품 하나에 대한 자녀의 사이즈 추천 결과다. 맞는 사이즈가 없거나 사이즈 무관 상품이면 추천 사이즈와 핏은 null이다.
 *
 * @param fit 추천 사이즈가 아이 몸에 맞는 정도의 이름
 * @param candidateSizes 추천 사이즈를 고른 후보 사이즈이고, 추천 사이즈가 없으면 빈 목록이다
 * @param lastPurchasedSize 같은 카테고리에서 이 자녀에게 산 사이즈다. 구매 내역을 아직 읽지 않아 늘 null이다
 */
public record SizeRecommendationResult(
    Long productId,
    Long childId,
    String recommendedSize,
    String fit,
    List<Notice> notices,
    List<String> candidateSizes,
    String lastPurchasedSize) {

    /**
     * @param code 안내 종류의 이름
     */
    public record Notice(String code) {
    }
}
