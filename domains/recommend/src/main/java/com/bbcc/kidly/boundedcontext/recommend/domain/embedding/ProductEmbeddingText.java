package com.bbcc.kidly.boundedcontext.recommend.domain.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * 상품의 뜻을 비교하려고 임베딩 API에 보내는 글이다. 이름 · 브랜드 · 상품 정보 값 · 설명 순으로 줄을 잇는다.
 */
public record ProductEmbeddingText(String value) {

    public static final int MAX_LENGTH = 4_000;

    /**
     * 상품 정보 값 하나다. 이벤트의 정보 값에서 code와 value만 옮긴다.
     */
    public record InfoValue(String code, String value) {
    }

    public static ProductEmbeddingText compose(String name, String brand, List<InfoValue> infoValues,
        String description) {
        List<String> lines = new ArrayList<>();
        lines.add("상품명: " + name);
        addIfPresent(lines, "브랜드", brand);
        for (InfoValue infoValue : infoValues) {
            lines.add(infoValue.code() + ": " + infoValue.value());
        }
        addIfPresent(lines, "설명", description);
        return new ProductEmbeddingText(String.join("\n", lines));
    }

    private static void addIfPresent(List<String> lines, String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(label + ": " + text);
    }
}
