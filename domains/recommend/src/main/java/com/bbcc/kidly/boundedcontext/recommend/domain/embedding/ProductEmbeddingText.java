package com.bbcc.kidly.boundedcontext.recommend.domain.embedding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        lines.addAll(infoValueLines(infoValues));
        addIfPresent(lines, "설명", description);
        return new ProductEmbeddingText(String.join("\n", lines));
    }

    // 이벤트 안의 순서가 달라도 같은 글이 나오도록 code와 값을 모두 오름차순으로 쓴다
    private static List<String> infoValueLines(List<InfoValue> infoValues) {
        Map<String, List<String>> valuesByCode = new TreeMap<>();
        for (InfoValue infoValue : infoValues) {
            if (infoValue.value() == null || infoValue.value().isBlank()) {
                continue;
            }
            valuesByCode.computeIfAbsent(infoValue.code(), code -> new ArrayList<>()).add(infoValue.value());
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : valuesByCode.entrySet()) {
            List<String> values = entry.getValue();
            Collections.sort(values);
            lines.add(entry.getKey() + ": " + String.join(", ", values));
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(label + ": " + text);
    }
}
