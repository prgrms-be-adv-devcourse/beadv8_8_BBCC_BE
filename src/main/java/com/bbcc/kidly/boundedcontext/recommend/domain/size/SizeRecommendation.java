package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class SizeRecommendation {

    private final String recommendedSize;
    private final Fit fit;
    private final List<SizeNotice> notices;

    public static SizeRecommendation noMatchingSize() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.NO_MATCHING_SIZE)
        );
    }

    public static SizeRecommendation sizeFree() {
        return new SizeRecommendation(
                null,
                null,
                List.of(SizeNotice.SIZE_FREE)
        );
    }

    public static SizeRecommendation recommended(String size, Fit fit, List<SizeNotice> notices) {
        return new SizeRecommendation(size, fit, notices);
    }
}
