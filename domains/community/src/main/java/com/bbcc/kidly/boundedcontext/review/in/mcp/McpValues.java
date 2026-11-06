package com.bbcc.kidly.boundedcontext.review.in.mcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 도구 응답 값 변환(시각은 ISO 문자열, 시간대 표시 없는 한국 시간).
 */
final class McpValues {

    private McpValues() {
    }

    static String time(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
