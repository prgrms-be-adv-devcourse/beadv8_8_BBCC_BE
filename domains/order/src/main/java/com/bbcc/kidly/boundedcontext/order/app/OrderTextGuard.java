package com.bbcc.kidly.boundedcontext.order.app;

import java.util.regex.Pattern;

/**
 * 밖(AI 등)으로 내보내는 글 칸 거르기(#925 · #926). 자유 문구(예외 메시지 · 메모 · 받은 메시지 값)에는 개인정보가 섞일 수 있으므로
 * 정해진 형식(코드 · 예외 클래스 이름 · 경로 틀 · 이벤트 타입 · ID)일 때만 그대로 돌려주고, 아니면 null이다(일부를 잘라 남기지 않는다).
 * 관리자 API 응답은 이것을 거치지 않는다. 여러 기능이 같이 쓰므로 app 바로 아래에 둔다.
 */
public final class OrderTextGuard {

    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern EXCEPTION_CLASS = Pattern.compile(IDENTIFIER + "(\\." + IDENTIFIER + ")*");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final Pattern KEBAB_CODE = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern SIMPLE_CLASS = Pattern.compile("[A-Z][A-Za-z0-9_$]*");
    private static final String WORD = "[a-z][a-z0-9]*(-[a-z0-9]+)*";
    private static final Pattern EVENT_TYPE = Pattern.compile(WORD + "(\\." + WORD + ")+");
    private static final Pattern SCHEDULER = Pattern.compile("scheduler:" + WORD);
    private static final Pattern HTTP_PATH = Pattern.compile(
        "(GET|POST|PUT|PATCH|DELETE) /api/v1(/([a-z0-9-]+|\\{[A-Za-z]+}))+");
    private static final Pattern NUMBER_ID = Pattern.compile("[0-9]{1,19}");
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern PRODUCER = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final int CLASS_MAX = 200;
    private static final int CODE_MAX = 64;
    private static final int SOURCE_MAX = 200;

    private OrderTextGuard() {
    }

    /**
     * "예외클래스: 메시지"(아웃박스 lastError 등)에서 ':' 앞의 예외 클래스 이름만. 클래스 이름 형식이 아니면 null.
     */
    public static String exceptionClassOnly(String lastError) {
        if (lastError == null) {
            return null;
        }
        int colon = lastError.indexOf(':');
        String name = (colon < 0 ? lastError : lastError.substring(0, colon)).strip();
        return matches(EXCEPTION_CLASS, name, CLASS_MAX) ? name : null;
    }

    /**
     * 대문자 코드(^[A-Z0-9_]{1,64}$)만 그대로, 아니면 null.
     */
    public static String codeOrNull(String reason) {
        return matches(CODE, reason, CODE_MAX) ? reason : null;
    }

    /**
     * 실패 · 사유 코드: 대문자 코드, 소문자 줄표 코드(member-mismatch 등 결제 불일치 사유), 예외 단순 이름(PaymentFailures가 남기는
     * IllegalStateException 등) 중 하나일 때만 그대로(64자 이하), 아니면 null. 이력 reason · 실패 기록 failureCode가 이 셋으로 만들어진다.
     */
    public static String failureCodeOrNull(String code) {
        return matches(CODE, code, CODE_MAX) || matches(KEBAB_CODE, code, CODE_MAX)
            || matches(SIMPLE_CLASS, code, CODE_MAX) ? code : null;
    }

    /**
     * 이력 source: HTTP 경로 틀(POST /api/v1/orders/{orderId}/cancel) · 스케줄러 작업(scheduler:payment-expiry) ·
     * 이벤트 타입(payment.completed)만 그대로, 아니면 null.
     */
    public static String sourceOrNull(String source) {
        return matches(HTTP_PATH, source, SOURCE_MAX) || matches(SCHEDULER, source, SOURCE_MAX)
            || eventTypeOrNull(source) != null ? source : null;
    }

    /**
     * 이벤트 타입(소문자 단어 · 줄표를 '.'로 이은 것, 예: order-item.cancelled)만 그대로, 아니면 null.
     */
    public static String eventTypeOrNull(String eventType) {
        return matches(EVENT_TYPE, eventType, SOURCE_MAX) ? eventType : null;
    }

    /**
     * ID(숫자 · UUID)만 그대로, 아니면 null. 받은 메시지의 키 · aggregateId 등.
     */
    public static String idOrNull(String id) {
        return matches(NUMBER_ID, id, 19) || matches(UUID, id, 36) ? id : null;
    }

    /**
     * 보낸 도메인 이름(소문자 · 줄표)만 그대로, 아니면 null.
     */
    public static String producerOrNull(String producer) {
        return matches(PRODUCER, producer, 32) ? producer : null;
    }

    private static boolean matches(Pattern pattern, String value, int max) {
        return value != null && !value.isEmpty() && value.length() <= max && pattern.matcher(value).matches();
    }
}
