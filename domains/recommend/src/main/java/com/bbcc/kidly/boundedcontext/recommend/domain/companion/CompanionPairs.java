package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ACCESSORY;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.PANTS;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.SKIRT;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.TOP;
import static java.util.Objects.requireNonNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 보고 있는 상품의 종류마다 함께 보여 줄 짝 종류를 우선순위 순으로 정한 표. */
public final class CompanionPairs {

    private static final Map<CompanionKind, List<CompanionKind>> TABLE = new EnumMap<>(CompanionKind.class);

    static {
        TABLE.put(TOP, List.of(PANTS, SKIRT, OUTER, ACCESSORY));
        TABLE.put(PANTS, List.of(TOP, ONE_PIECE, OUTER, ACCESSORY));
        TABLE.put(SKIRT, List.of(TOP, OUTER, ACCESSORY));
        TABLE.put(ONE_PIECE, List.of(PANTS, OUTER, ACCESSORY));
        TABLE.put(OUTER, List.of(PANTS, SKIRT, TOP, ONE_PIECE, ACCESSORY));
        TABLE.put(ACCESSORY, List.of(TOP, PANTS, SKIRT, ONE_PIECE, OUTER));
    }

    private CompanionPairs() {
    }

    public static List<CompanionKind> of(CompanionKind viewed) {
        requireNonNull(viewed, "viewed는 필수입니다");
        return TABLE.get(viewed);
    }
}
