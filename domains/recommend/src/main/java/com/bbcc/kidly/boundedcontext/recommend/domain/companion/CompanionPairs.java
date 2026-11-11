package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ACCESSORY;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.ONE_PIECE;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.OUTER;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.PANTS;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.SKIRT;
import static com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionKind.TOP;
import static java.util.Objects.requireNonNull;

import java.util.List;

/** 보고 있는 상품의 종류마다 함께 보여 줄 짝 종류를 우선순위 순으로 정한 표. */
public final class CompanionPairs {

    private CompanionPairs() {
    }

    public static List<CompanionKind> of(CompanionKind viewed) {
        requireNonNull(viewed, "viewed는 필수입니다");
        // 종류를 더하고 짝을 빠뜨리면 컴파일이 실패하도록 switch로 둔다
        return switch (viewed) {
            case TOP -> List.of(PANTS, SKIRT, OUTER, ACCESSORY);
            case PANTS -> List.of(TOP, ONE_PIECE, OUTER, ACCESSORY);
            case SKIRT -> List.of(TOP, OUTER, ACCESSORY);
            case ONE_PIECE -> List.of(PANTS, OUTER, ACCESSORY);
            case OUTER -> List.of(PANTS, SKIRT, TOP, ONE_PIECE, ACCESSORY);
            case ACCESSORY -> List.of(TOP, PANTS, SKIRT, ONE_PIECE, OUTER);
        };
    }
}
