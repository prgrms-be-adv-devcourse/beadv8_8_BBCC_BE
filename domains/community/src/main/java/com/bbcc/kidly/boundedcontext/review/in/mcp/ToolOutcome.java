package com.bbcc.kidly.boundedcontext.review.in.mcp;

import java.util.Map;

/**
 * 도구 호출 결과. 정상이면 구조화 결과, 오류면 정해진 문구(받은 값 · 예외 메시지를 넣지 않는다).
 */
public record ToolOutcome(boolean error, Map<String, Object> structured, String message) {

    public static ToolOutcome ok(Map<String, Object> structured) {
        return new ToolOutcome(false, structured, null);
    }

    public static ToolOutcome failure(String message) {
        return new ToolOutcome(true, null, message);
    }
}
