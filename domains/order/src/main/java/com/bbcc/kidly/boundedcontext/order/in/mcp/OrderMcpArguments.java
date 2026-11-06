package com.bbcc.kidly.boundedcontext.order.in.mcp;

import java.util.Map;

/**
 * MCP 도구 인자 읽기. 오류 메시지에는 인자 이름만 넣고 값은 넣지 않는다.
 */
record OrderMcpArguments(Map<String, Object> values, int maxResults) {

    OrderMcpArguments {
        values = values == null ? Map.of() : values;
    }

    String text(String name) {
        Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new InvalidArgumentException(name + "는 문자열이어야 합니다");
        }
        return text.isBlank() ? null : text.strip();
    }

    Long id(String name) {
        Long id = longOf(name, values.get(name));
        if (id == null || id <= 0) {
            throw new InvalidArgumentException(name + "는 0보다 큰 숫자 ID여야 합니다");
        }
        return id;
    }

    int page() {
        Long page = longOf("page", values.get("page"));
        if (page == null) {
            return 0;
        }
        if (page < 0 || page > Integer.MAX_VALUE) {
            throw new InvalidArgumentException("page는 0 이상이어야 합니다");
        }
        return page.intValue();
    }

    /**
     * 없으면 기본(20, 상한이 더 작으면 상한), 상한보다 크면 상한으로 줄인다.
     */
    int size() {
        Long size = longOf("size", values.get("size"));
        if (size == null) {
            return Math.min(OrderMcpTools.DEFAULT_SIZE, maxResults);
        }
        if (size < 1) {
            throw new InvalidArgumentException("size는 1 이상이어야 합니다");
        }
        return (int) Math.min(size, maxResults);
    }

    private static Long longOf(String name, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return ((Number) value).longValue();
        }
        if (value instanceof String text && !text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9')) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new InvalidArgumentException(name + "가 너무 큽니다");
            }
        }
        throw new InvalidArgumentException(name + "는 정수여야 합니다");
    }

    static class InvalidArgumentException extends RuntimeException {

        InvalidArgumentException(String message) {
            super(message);
        }
    }
}
