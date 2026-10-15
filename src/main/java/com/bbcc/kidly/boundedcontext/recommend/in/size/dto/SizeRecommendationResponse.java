package com.bbcc.kidly.boundedcontext.recommend.in.size.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @param recommendedSize 맞는 사이즈가 없거나 사이즈 무관 상품이면 null
 * @param fit 추천 사이즈가 없으면 null
 * @param candidateSizes 추천 사이즈가 없으면 빈 목록
 * @param lastPurchasedSize 같은 카테고리에서 이 자녀에게 산 사이즈. 지금은 늘 null
 */
public record SizeRecommendationResponse(
    String productId,
    String childId,
    String recommendedSize,
    String fit,
    List<Notice> notices,
    List<String> candidateSizes,
    String lastPurchasedSize) {

    public record Notice(String code) {
    }

    public static SizeRecommendationResponse from(SizeRecommendationResult result) {
        List<Notice> notices = new ArrayList<>();
        for (SizeRecommendationResult.Notice notice : result.notices()) {
            notices.add(new Notice(notice.code()));
        }
        return new SizeRecommendationResponse(
            String.valueOf(result.productId()),
            String.valueOf(result.childId()),
            result.recommendedSize(),
            result.fit(),
            notices,
            result.candidateSizes(),
            result.lastPurchasedSize());
    }
}
