package com.bbcc.kidly.boundedcontext.recommend.domain.embedding;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 상품의 뜻을 비교하려고 임베딩 API에 보내는 글이다. 이름 · 카테고리 경로 · 브랜드 · 상품 정보 값 · 설명 순으로 줄을 잇는다.
 * 카테고리 줄이 앞쪽에 있어서 글이 너무 길어 끝이 잘려도 카테고리는 남고 설명이 먼저 잘린다.
 */
public record ProductEmbeddingText(String value) {

    public static final int MAX_LENGTH = 4_000;

    private static final String CATEGORY_SEPARATOR = " > ";
    private static final String NAME_LABEL = "상품명";
    private static final String CATEGORY_LABEL = "카테고리";
    private static final String BRAND_LABEL = "브랜드";
    private static final String DESCRIPTION_LABEL = "설명";
    private static final String DETAIL_SEPARATOR = " / ";

    public record InfoValue(String code, String value) {
    }

    public static ProductEmbeddingText compose(String name, List<String> categoryPath, String brand,
        List<InfoValue> infoValues, String description) {
        requireNonNull(name, "name은 필수입니다");
        List<String> lines = new ArrayList<>();
        lines.add(NAME_LABEL + ": " + name);
        addIfPresent(lines, CATEGORY_LABEL, joinCategoryNames(categoryPath));
        addIfPresent(lines, BRAND_LABEL, brand);
        lines.addAll(infoValueLines(infoValues == null ? List.of() : infoValues));
        addIfPresent(lines, DESCRIPTION_LABEL, description);
        return new ProductEmbeddingText(truncate(String.join("\n", lines)));
    }

    /**
     * 상품명 · 카테고리 · 브랜드 줄을 뺀 나머지 줄(상품 정보 값 줄과 설명 줄)을 " / "로 이은 글이다. 남는 줄이 없으면 null이다.
     */
    public String detailText() {
        List<String> details = new ArrayList<>();
        for (String line : value.split("\n")) {
            if (!isHeadLine(line)) {
                details.add(line);
            }
        }
        if (details.isEmpty()) {
            return null;
        }
        return String.join(DETAIL_SEPARATOR, details);
    }

    private static boolean isHeadLine(String line) {
        return line.startsWith(NAME_LABEL + ": ")
            || line.startsWith(CATEGORY_LABEL + ": ")
            || line.startsWith(BRAND_LABEL + ": ");
    }

    private static String truncate(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH));
    }

    private static String joinCategoryNames(List<String> categoryPath) {
        if (categoryPath == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String name : categoryPath) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(CATEGORY_SEPARATOR, names);
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
