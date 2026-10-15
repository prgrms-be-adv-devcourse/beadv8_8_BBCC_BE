package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class SizeRecommendation {

    private final String recommendedSize;
    private final Fit fit;
    private final List<SizeNotice> notices;
    private final List<String> candidateSizes;

    public static SizeRecommendation noMatchingSize() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.NO_MATCHING_SIZE),
                List.of()
        );
    }

    public static SizeRecommendation sizeFree() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.SIZE_FREE),
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
        return new SizeRecommendation(size, fit, List.copyOf(notices), List.copyOf(candidateSizes));
    }
}
