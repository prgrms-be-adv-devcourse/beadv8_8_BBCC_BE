package com.bbcc.kidly.boundedcontext.recommend.in.size.dto;

import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @param recommendedSize 맞는 사이즈가 없거나 사이즈 무관 상품이면 null
 * @param fit 추천 사이즈가 없으면 null
 * @param candidateSizes 규칙이 고른 후보 사이즈. 규칙이 맞는 사이즈를 찾지 못하면 빈 목록이고, AI가 고른 추천 사이즈는 이 목록 밖일 수 있다
 * @param lastPurchasedSize 같은 카테고리에서 이 자녀에게 산 사이즈. 지금은 늘 null
 * @param source 추천을 정한 쪽. AI 또는 RULE
 * @param reasons 사이즈를 고른 근거. 없으면 빈 목록
 * @param referenceFit 기준 옷과 이 상품을 비교한 기준 핏. 기준 옷이 없거나 비교할 수 없으면 null
 * @param itemJudgements AI 답을 채택했을 때의 항목별 판단. 규칙 결과이거나 판단이 없으면 빈 목록
 */
public record SizeRecommendationResponse(
    String productId,
    String childId,
    String recommendedSize,
    String fit,
    List<Notice> notices,
    List<String> candidateSizes,
    String lastPurchasedSize,
    String source,
    List<Reason> reasons,
    ReferenceFit referenceFit,
    List<ItemJudgement> itemJudgements) {

    public record Notice(String code, String message) {
    }

    public record Reason(String type, String detail, String sourceId) {
    }

    public record ReferenceFit(String productPart, String comparedPart, List<FitItem> items) {
    }

    public record FitItem(String code, BigDecimal garmentCm, List<SizeDiff> sizes) {
    }

    public record SizeDiff(String size, BigDecimal productCm, BigDecimal diffCm) {
    }

    public record ItemJudgement(String code, BigDecimal diffCm, String fit, BigDecimal allowanceMinCm,
                                BigDecimal allowanceMaxCm) {
    }

    public static SizeRecommendationResponse from(SizeRecommendationResult result) {
        List<Notice> notices = new ArrayList<>();
        for (SizeRecommendationResult.Notice notice : result.notices()) {
            notices.add(new Notice(notice.code(), notice.message()));
        }
        List<Reason> reasons = new ArrayList<>();
        for (SizeRecommendationResult.Reason reason : result.reasons()) {
            reasons.add(new Reason(reason.type(), reason.detail(), reason.sourceId()));
        }
        List<ItemJudgement> itemJudgements = new ArrayList<>();
        for (SizeRecommendationResult.ItemJudgement judgement : result.itemJudgements()) {
            itemJudgements.add(new ItemJudgement(judgement.code(), judgement.diffCm(), judgement.fit(),
                judgement.allowanceMinCm(), judgement.allowanceMaxCm()));
        }
        return new SizeRecommendationResponse(
            String.valueOf(result.productId()),
            String.valueOf(result.childId()),
            result.recommendedSize(),
            result.fit(),
            notices,
            result.candidateSizes(),
            result.lastPurchasedSize(),
            result.source(),
            reasons,
            referenceFitOf(result.referenceFit()),
            itemJudgements);
    }

    private static ReferenceFit referenceFitOf(SizeRecommendationResult.ReferenceFit referenceFit) {
        if (referenceFit == null) {
            return null;
        }
        List<FitItem> items = new ArrayList<>();
        for (SizeRecommendationResult.FitItem item : referenceFit.items()) {
            List<SizeDiff> sizes = new ArrayList<>();
            for (SizeRecommendationResult.SizeDiff diff : item.sizes()) {
                sizes.add(new SizeDiff(diff.size(), diff.productCm(), diff.diffCm()));
            }
            items.add(new FitItem(item.code(), item.garmentCm(), sizes));
        }
        return new ReferenceFit(referenceFit.productPart(), referenceFit.comparedPart(), items);
    }
}
