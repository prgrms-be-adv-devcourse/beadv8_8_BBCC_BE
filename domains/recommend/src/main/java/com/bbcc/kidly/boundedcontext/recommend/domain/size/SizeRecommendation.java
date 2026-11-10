package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    public static SizeRecommendation noMatchingSize() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.NO_MATCHING_SIZE),
                List.of(),
                SizeRecommendationSource.RULE,
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
                List.of()
        );
    }

    public static SizeRecommendation recommended(
            String size,
            Fit fit,
            List<SizeNotice> notices,
            List<String> candidateSizes
    ) {
        // 추천 사이즈가 있으면 적합도도 있어야 하고, 추천 사이즈는 늘 후보 중 하나다 (INV-R04)
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
                List.of()
        );
    }

    /**
     * 규칙 결과에 AI 답을 합친 최종 결과를 돌려준다. AI가 고른 사이즈는 규칙 후보 밖이어도 상품에 있으면 채택한다.
     */
    public SizeRecommendation decideWith(ProductSizes productSizes, AiSizeAnswer answer) {
        if (!isProductSize(productSizes, answer.recommendedSize())) {
            return this;
        }
        List<SizeReason> aiReasons = new ArrayList<>();
        for (AiSizeAnswer.Reason reason : answer.reasons()) {
            aiReasons.add(new SizeReason(SizeReasonType.valueOf(reason.type()), reason.detail(), reason.sourceId()));
        }
        return new SizeRecommendation(
                answer.recommendedSize(),
                Fit.valueOf(answer.fit()),
                List.of(),
                candidateSizes,
                SizeRecommendationSource.AI,
                List.copyOf(aiReasons)
        );
    }

    private static boolean isProductSize(ProductSizes productSizes, String size) {
        for (SizeRange range : productSizes.sizeRanges()) {
            if (range.size().equals(size)) {
                return true;
            }
        }
        return false;
    }
}
