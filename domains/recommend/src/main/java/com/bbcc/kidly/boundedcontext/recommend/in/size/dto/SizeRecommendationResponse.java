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
 * @param growthSpeed 자녀의 키 성장 속도이고 늘 있다. 숫자는 소수 둘째 자리 반올림이고, 최근 속도는 기록이 부족하면 null이다
 * @param wearablePeriods 사이즈별로 앞으로 입을 수 있는 개월 수(24개월 상한). 사이즈 무관 상품이나 모자 · 양말이면 빈 목록이고, 계산할 수 없는 칸은 null
 * @param wearPreview 자녀가 상품 사이즈를 입은 모습. 사이즈 무관 상품이나 모자 · 양말이면 null이다.
 *     높이는 바닥에서 잰 cm이고, 프런트는 위치를 cm로 다시 계산하지 않고 hemAt · sleeveAt을 그대로 쓴다
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
    List<WearablePeriod> wearablePeriods,
    WearPreview wearPreview) {

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

    /**
     * @param garment 상품의 옷 종류. TOP, BOTTOM, OUTER, DRESS, SKIRT
     * @param body 자녀의 몸 높이
     * @param sizes 상품 사이즈별 착용 결과이고 사이즈 순서는 상품과 같다
     */
    public record WearPreview(String garment, Body body, List<SizeWear> sizes) {
    }

    /**
     * 바닥에서 잰 높이(cm)이고 소수 첫째 자리까지 반올림한 값이다. 팔은 길이다.
     */
    public record Body(double neckCm, double shoulderCm, double waistCm, double hipCm, double crotchCm,
                       double kneeCm, double ankleCm, double armCm) {
    }

    /**
     * @param hemCm 밑단 높이(cm)이고, 밑단이 바닥보다 아래면 0이다. 총장 실측이 없으면 null
     * @param hemAt 밑단 위치. ABOVE_WAIST, WAIST_HIP, HIP, THIGH, ABOVE_KNEE, KNEE, BELOW_KNEE, ANKLE, FLOOR
     * @param sleeveCm 소매 끝 길이(cm). 소매길이 실측이 없거나 바지 · 치마 · 원피스면 null
     * @param sleeveAt 소매 위치. ELBOW(팔꿈치까지 또는 그 위), ABOVE_WRIST, WRIST, HAND
     */
    public record SizeWear(String size, Double hemCm, String hemAt, Double sleeveCm, String sleeveAt) {
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
            wearablePeriods,
            wearPreviewOf(result.wearPreview()));
    }

    private static WearPreview wearPreviewOf(SizeRecommendationResult.WearPreview preview) {
        if (preview == null) {
            return null;
        }
        SizeRecommendationResult.Body body = preview.body();
        List<SizeWear> sizes = new ArrayList<>();
        for (SizeRecommendationResult.SizeWear wear : preview.sizes()) {
            sizes.add(new SizeWear(wear.size(), wear.hemCm(), wear.hemAt(), wear.sleeveCm(), wear.sleeveAt()));
        }
        return new WearPreview(preview.garment(),
            new Body(body.neckCm(), body.shoulderCm(), body.waistCm(), body.hipCm(), body.crotchCm(),
                body.kneeCm(), body.ankleCm(), body.armCm()),
            sizes);
    }

    private static GrowthSpeed growthSpeedOf(SizeRecommendationResult.GrowthSpeed growthSpeed) {
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
