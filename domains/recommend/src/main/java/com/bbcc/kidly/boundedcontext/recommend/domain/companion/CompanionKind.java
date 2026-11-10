package com.bbcc.kidly.boundedcontext.recommend.domain.companion;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 함께 사면 좋은 상품을 고를 때 쓰는 상품 종류. */
public enum CompanionKind {
    TOP,
    BOTTOM,
    ONE_PIECE,
    OUTER,
    ACCESSORY;

    private static final String ACCESSORY_ROOT_NAME = "잡화";

    // 카테고리에 코드 칸이 없어 말단 카테고리 이름으로 종류를 맞춘다.
    private static final Map<String, CompanionKind> CLOTHING_LEAF_NAMES = Map.of(
        "상의", TOP,
        "하의", BOTTOM,
        "원피스", ONE_PIECE,
        "아우터", OUTER);

    public static Optional<CompanionKind> of(List<String> categoryPathNames) {
        requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다");
        // 카테고리 경로 칸이 생기기 전에 받은 상품은 경로가 빈 목록이다
        if (categoryPathNames.isEmpty()) {
            return Optional.empty();
        }
        if (categoryPathNames.getFirst().equals(ACCESSORY_ROOT_NAME)) {
            return Optional.of(ACCESSORY);
        }
        String leafName = categoryPathNames.getLast();
        return Optional.ofNullable(CLOTHING_LEAF_NAMES.get(leafName));
    }
}
