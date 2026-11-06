package com.bbcc.kidly.boundedcontext.review.in.mcp;

import java.util.regex.Pattern;

/**
 * 자유 문구가 될 수 있는 칸(토픽 · eventType · 예외 이름 · 메시지 키 · 이벤트 ID)을 정해진 형식일 때만 내보낸다. 아니면 null.
 * 받은 값에 개인정보나 예외 메시지가 섞여 들어와도 응답으로 새지 않게 한다.
 */
public final class McpSafeText {

    private static final Pattern TOPIC = Pattern.compile("[a-z0-9][a-z0-9._-]{0,199}");
    private static final Pattern EVENT_TYPE = Pattern.compile(
        "[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)+");
    private static final Pattern EXCEPTION_NAME = Pattern.compile("[A-Z][A-Za-z0-9]{0,99}");
    // 저장 형식: 패키지(소문자 마디) + 클래스 이름(중첩이면 $로 이음). 공백 · 콜론 · 메시지가 섞이면 맞지 않는다
    private static final Pattern QUALIFIED_EXCEPTION_NAME = Pattern.compile(
        "(?:[a-z][a-z0-9_]*\\.)*[A-Z][A-Za-z0-9_]*(?:\\$[A-Z][A-Za-z0-9_]*)*");
    private static final Pattern IDENTIFIER = Pattern.compile(
        "[0-9]{1,19}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private McpSafeText() {
    }

    public static String topic(String value) {
        return matches(TOPIC, value);
    }

    public static String eventType(String value) {
        return value != null && value.length() <= 100 ? matches(EVENT_TYPE, value) : null;
    }

    /**
     * 예외 클래스 이름을 단순 이름으로 내보낸다. 저장 값은 패키지가 붙은 전체 이름(getClass().getName())이라 마지막 . · $ 뒤만
     * 남긴다(출력 칸을 짧고 일정하게). 메시지 · 스택 · 공백 · 콜론이 섞였거나 형식이 다르면 null.
     */
    public static String exceptionName(String value) {
        if (value == null || value.length() > 300 || !QUALIFIED_EXCEPTION_NAME.matcher(value).matches()) {
            return null;
        }
        String simple = value.substring(Math.max(value.lastIndexOf('.'), value.lastIndexOf('$')) + 1);
        return matches(EXCEPTION_NAME, simple);
    }

    /**
     * 숫자 ID 또는 소문자 UUID(메시지 키 · 이벤트 ID).
     */
    public static String identifier(String value) {
        return matches(IDENTIFIER, value);
    }

    private static String matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches() ? value : null;
    }
}
