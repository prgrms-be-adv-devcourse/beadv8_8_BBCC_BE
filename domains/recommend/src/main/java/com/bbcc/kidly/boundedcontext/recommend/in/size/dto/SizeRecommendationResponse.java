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
 * @param growthSpeed 자녀의 키 성장 속도. 숫자는 소수 둘째 자리 반올림이고, 최근 속도는 기록이 부족하면 null
 * @param wearablePeriods 사이즈별로 앞으로 입을 수 있는 개월 수(24개월 상한). 사이즈 무관 상품이나 모자 · 양말이면 빈 목록이고, 계산할 수 없는 칸은 null
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
    List<ItemJudgement> itemJudgements,
    GrowthSpeed growthSpeed,
    List<WearablePeriod> wearablePeriods) {

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

    public record GrowthSpeed(BigDecimal cmPerMonth, BigDecimal recentCmPerMonth, int recordCount,
                              BigDecimal recordMonths, int childSharePercent, BigDecimal tableCmPerMonth,
                              String tableSource) {
    }

    public record WearablePeriod(String size, Integer lengthMonths, Integer widthMonths, Integer months) {
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
        List<WearablePeriod> wearablePeriods = new ArrayList<>();
        for (SizeRecommendationResult.WearablePeriod period : result.wearablePeriods()) {
            wearablePeriods.add(new WearablePeriod(
                period.size(), period.lengthMonths(), period.widthMonths(), period.months()));
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
            itemJudgements,
            growthSpeedOf(result.growthSpeed()),
            wearablePeriods);
    }

    private static GrowthSpeed growthSpeedOf(SizeRecommendationResult.GrowthSpeed growthSpeed) {
        if (growthSpeed == null) {
            return null;
        }
        return new GrowthSpeed(growthSpeed.cmPerMonth(), growthSpeed.recentCmPerMonth(), growthSpeed.recordCount(),
            growthSpeed.recordMonths(), growthSpeed.childSharePercent(), growthSpeed.tableCmPerMonth(),
            growthSpeed.tableSource());
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
