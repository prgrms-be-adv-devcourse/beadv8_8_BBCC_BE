package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class SizeRecommendation {

    private final String recommendedSize;
    private final Fit fit;
    private final List<SizeNotice> notices;
    private final List<String> candidateSizes;
    private final SizeRecommendationSource source;
    private final List<SizeReason> reasons;
    private final List<ItemJudgement> itemJudgements;

    public static SizeRecommendation noMatchingSize() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.NO_MATCHING_SIZE),
                List.of(),
                SizeRecommendationSource.RULE,
                List.of(),
                List.of()
        );
    }

    public static SizeRecommendation sizeFree() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.SIZE_FREE),
                List.of(),
                SizeRecommendationSource.RULE,
                List.of(),
                List.of()
        );
    }

    public static SizeRecommendation recommended(
            String size,
            Fit fit,
            List<SizeNotice> notices,
            List<String> candidateSizes
    ) {
        // 규칙 결과는 추천 사이즈가 있으면 적합도도 있어야 하고, 추천 사이즈는 늘 후보 중 하나다 (INV-R04)
        requireNonNull(size, "size는 필수입니다");
        requireNonNull(fit, "fit은 필수입니다");
        requireNonNull(notices, "notices는 필수입니다");
        requireNonNull(candidateSizes, "candidateSizes는 필수입니다");
        if (!candidateSizes.contains(size)) {
            throw new IllegalArgumentException("추천 사이즈는 후보 사이즈 중 하나여야 합니다");
        }
        return new SizeRecommendation(
                size,
                fit,
                List.copyOf(notices),
                List.copyOf(candidateSizes),
                SizeRecommendationSource.RULE,
                List.of(),
                List.of()
        );
    }

    /**
     * 규칙 결과에 AI 답을 합친 최종 결과를 돌려준다. AI가 고른 사이즈는 규칙 후보 밖이어도 상품에 있으면 채택한다.
     * 사이즈 무관 상품이거나, AI 답이 없거나, 상품에 없는 사이즈이거나, 적합도가 모르는 값이거나,
     * 항목별 판단이 넘긴 기준 핏과 맞지 않으면 규칙 결과를 그대로 돌려준다.
     * AI 답을 채택하면 후보는 규칙 후보를 그대로 두고, 규칙 계산을 설명하는 안내는 비우고, 쓸 수 있는 근거만 남긴다.
     */
    public SizeRecommendation decideWith(ProductSizes productSizes, AiSizeAnswer answer, SizeAiEvidence evidence) {
        requireNonNull(productSizes, "productSizes는 필수입니다");
        // 사이즈 무관 상품도 사이즈 이름(FREE 등)을 가질 수 있어, 상품 사이즈 검사보다 먼저 거른다
        if (productSizes.isSizeFree()) {
            return this;
        }
        if (answer == null || !productSizes.hasSize(answer.recommendedSize())) {
            return this;
        }
        Fit aiFit = Fit.findByName(answer.fit());
        if (aiFit == null) {
            return this;
        }
        Optional<List<ItemJudgement>> judgements = ItemJudgementVerifier.verify(
                answer.itemJudgements(), answer.recommendedSize(), evidence);
        if (judgements.isEmpty()) {
            return this;
        }
        return new SizeRecommendation(
                answer.recommendedSize(),
                aiFit,
                List.of(),
                candidateSizes,
                SizeRecommendationSource.AI,
                usableReasons(answer.reasons()),
                judgements.get()
        );
    }

    // 글 조각을 AI에 주지 않으므로 AI가 돌려준 sourceId는 믿을 수 없어 늘 null로 둔다.
    private static List<SizeReason> usableReasons(List<AiSizeAnswer.Reason> aiReasons) {
        if (aiReasons == null) {
            return List.of();
        }
        List<SizeReason> reasons = new ArrayList<>();
        for (AiSizeAnswer.Reason aiReason : aiReasons) {
            if (aiReason == null || aiReason.detail() == null || aiReason.detail().isBlank()) {
                continue;
            }
            SizeReasonType type = findByName(SizeReasonType.class, aiReason.type());
            if (type != null) {
                reasons.add(new SizeReason(type, aiReason.detail(), null));
            }
        }
        return List.copyOf(reasons);
    }

    // 모델이 준 문자열이 모르는 값일 수 있어, valueOf의 예외 대신 이름이 정확히 같은지 비교하고 없으면 null을 돌려준다.
    private static <E extends Enum<E>> E findByName(Class<E> type, String name) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }
}
