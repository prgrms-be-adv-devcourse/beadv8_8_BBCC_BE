package com.bbcc.kidly.boundedcontext.recommend.app.size.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 하나에 대한 자녀의 사이즈 추천 결과다. 맞는 사이즈가 없거나 사이즈 무관 상품이면 추천 사이즈와 핏은 null이다.
 *
 * @param fit 추천 사이즈가 아이 몸에 맞는 정도의 이름
 * @param candidateSizes 규칙이 추천 사이즈를 고른 후보 사이즈이고, 규칙이 맞는 사이즈를 찾지 못하면 빈 목록이다.
 *     AI가 고른 추천 사이즈는 이 목록 밖의 상품 사이즈일 수 있다
 * @param lastPurchasedSize 같은 카테고리에서 이 자녀에게 산 사이즈다. 구매 내역을 아직 읽지 않아 늘 null이다
 * @param source 추천을 정한 쪽의 이름이다. AI 또는 RULE이다
 * @param reasons 사이즈를 고른 근거이고, 근거가 없으면 빈 목록이다
 * @param referenceFit 자녀의 기준 옷과 이 상품을 비교한 기준 핏이고, 기준 옷이 없거나 비교할 수 없으면 null이다
 * @param itemJudgements AI 답을 채택했을 때의 실측 항목별 판단이고, 규칙 결과이거나 판단이 없으면 빈 목록이다
 */
public record SizeRecommendationResult(
    Long productId,
    Long childId,
    String recommendedSize,
    String fit,
    List<Notice> notices,
    List<String> candidateSizes,
    String lastPurchasedSize,
    String source,
    List<Reason> reasons,
    ReferenceFit referenceFit,
    List<ItemJudgement> itemJudgements) {

    /**
     * @param code 안내 종류의 이름
     * @param message 화면에 그대로 보여 주는 문구
     */
    public record Notice(String code, String message) {
    }

    /**
     * @param type 근거 종류의 이름
     * @param detail 화면에 그대로 보여 주는 문구
     * @param sourceId 근거로 쓴 글 조각의 ID이고, 없으면 null이다
     */
    public record Reason(String type, String detail, String sourceId) {
    }

    /**
     * @param productPart 상품과 같은 종류의 칸의 이름
     * @param comparedPart 실제로 비교한 기준 옷 칸의 이름이고, 같은 종류의 기준 옷이 없어 대신했으면 productPart와 다르다
     */
    public record ReferenceFit(String productPart, String comparedPart, List<FitItem> items) {
    }

    /**
     * @param code 실측 항목 코드
     * @param garmentCm 기준 옷의 실측(cm)
     */
    public record FitItem(String code, BigDecimal garmentCm, List<SizeDiff> sizes) {
    }

    /**
     * @param productCm 그 사이즈의 상품 실측(cm)
     * @param diffCm 상품 실측에서 기준 옷 실측을 뺀 값(cm)
     */
    public record SizeDiff(String size, BigDecimal productCm, BigDecimal diffCm) {
    }

    /**
     * @param code 실측 항목 코드
     * @param diffCm 그 항목의 추천 사이즈와 기준 옷의 차이(cm)
     * @param fit 판단의 이름
     * @param allowanceMinCm 판단에 쓴 여유 범위의 최소(cm)
     * @param allowanceMaxCm 판단에 쓴 여유 범위의 최대(cm)
     */
    public record ItemJudgement(String code, BigDecimal diffCm, String fit, BigDecimal allowanceMinCm,
                                BigDecimal allowanceMaxCm) {
    }
}
