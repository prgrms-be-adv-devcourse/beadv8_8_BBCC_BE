package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.boundedcontext.order.domain.order.PaymentMismatchException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 밖(AI 등)으로 내보내는 글 칸 거르기(#925 · #926). 자유 문구(예외 메시지 · 메모 · 받은 메시지 값)에는 개인정보가 섞일 수 있으므로
 * 정해진 형식(코드 · 예외 클래스 이름 · 경로 틀 · 이벤트 타입 · ID)일 때만 그대로 돌려주고, 아니면 null이다(일부를 잘라 남기지 않는다).
 * 형식은 실제로 만들어지는 값만 통과하도록 좁게 둔다(전화번호 · 이름 같은 ASCII 개인정보 형태도 막는다).
 * 관리자 API 응답은 이것을 거치지 않는다. 여러 기능이 같이 쓰므로 app 바로 아래에 둔다.
 */
public final class OrderTextGuard {

    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final String THROWABLE = "[A-Za-z_$][A-Za-z0-9_$]*(Exception|Error)";
    private static final Pattern EXCEPTION_CLASS = Pattern.compile("(" + IDENTIFIER + "\\.)*" + THROWABLE);
    private static final Pattern SIMPLE_EXCEPTION = Pattern.compile("[A-Z][A-Za-z0-9_$]*(Exception|Error)");
    // 대문자로 시작해야 한다(숫자만인 전화번호 · 주민번호 형태를 막는다)
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    // 소문자 줄표 실패 코드는 결제 불일치 사유(PaymentFailures가 남김)뿐이다
    private static final Set<String> MISMATCH_CODES = Arrays.stream(PaymentMismatchException.Reason.values())
        .map(PaymentMismatchException.Reason::code)
        .collect(Collectors.toUnmodifiableSet());
    private static final String WORD = "[a-z][a-z0-9]*(-[a-z0-9]+)*";
    private static final Pattern EVENT_TYPE = Pattern.compile(WORD + "(\\." + WORD + ")+");
    private static final Pattern SCHEDULER = Pattern.compile("scheduler:" + WORD);
    private static final Pattern HTTP_PATH = Pattern.compile(
        "(GET|POST|PUT|PATCH|DELETE) /api/v1(/([a-z0-9-]+|\\{[A-Za-z]+}))+");
    // 0으로 시작하지 않는 양수(전화번호 형태를 막는다)
    private static final Pattern NUMBER_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern DOMAIN_TOPIC = Pattern.compile("kidly\\.([a-z][a-z0-9-]{0,31})\\.events");
    // 받은 eventType의 앞 마디로 허용하는 값(토픽별). 목록에 없는 토픽은 보낸 도메인 x 하나(x.)다.
    // 주문이 구독하는 토픽(OrderKafkaListenerConfig)과 shared 이벤트 이름(DomainEvent: 클래스 이름 → {대상}.{과거형})으로 맞춘다:
    //   kidly.payment.events     payment.completed · payment.failed 등          → 기본(payment.)
    //   kidly.seller.events      seller.changed · seller.application-rejected   → 기본(seller.)
    //   kidly.product.events     product.changed · stock.deducted · stock.deduction-failed → product. · stock.
    //   kidly.childprofile.events child.changed(보낸 도메인 childprofile)         → child.
    //   kidly.member.events      member.withdrawn(#1001)                         → 기본(member.)
    //   kidly.auth.events        child-account.changed(auth.tokens-revoked는 주문이 건너뛰므로 허용하지 않음) → child-account.
    //   kidly.order.events       cart.item-purchased(자기 토픽, 조르기 결제 완료 #999. 다른 종류는 건너뛰므로 실패하지 않음) → cart.
    // 구독 토픽 · shared 이벤트가 늘면 여기에 더한다
    private static final Map<String, Set<String>> RECEIVED_EVENT_PREFIXES = Map.of(
        "kidly.product.events", Set.of("product.", "stock."),
        "kidly.childprofile.events", Set.of("child."),
        "kidly.auth.events", Set.of("child-account."),
        "kidly.order.events", Set.of("cart."));
    private static final int CLASS_MAX = 200;
    private static final int CODE_MAX = 64;
    private static final int SOURCE_MAX = 200;

    private OrderTextGuard() {
    }

    /**
     * "예외클래스: 메시지"(아웃박스 lastError 등)에서 ':' 앞의 예외 클래스 이름만. 마지막 마디가 Exception · Error로 끝나는
     * 클래스 이름 형식이 아니면 null.
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
     * 대문자 코드(^[A-Z][A-Z0-9_]{0,63}$, 대문자로 시작)만 그대로, 아니면 null.
     */
    public static String codeOrNull(String reason) {
        return matches(CODE, reason, CODE_MAX) ? reason : null;
    }

    /**
     * 실패 · 사유 코드: 대문자 코드, 결제 불일치 사유 코드(member-mismatch 등, 목록 그대로), 예외 단순 이름(…Exception · …Error,
     * PaymentFailures가 남김) 중 하나일 때만 그대로, 아니면 null. 이력 reason · 실패 기록 failureCode가 이 셋으로 만들어진다.
     */
    public static String failureCodeOrNull(String code) {
        return matches(CODE, code, CODE_MAX) || (code != null && MISMATCH_CODES.contains(code))
            || matches(SIMPLE_EXCEPTION, code, CODE_MAX) ? code : null;
    }

    /**
     * 이력 source: HTTP 경로 틀(POST /api/v1/orders/{orderId}/cancel) · 스케줄러 작업(scheduler:payment-expiry) ·
     * 이벤트 타입(payment.completed)만 그대로, 아니면 null. 주문 코드가 만드는 값이다.
     */
    public static String sourceOrNull(String source) {
        return matches(HTTP_PATH, source, SOURCE_MAX) || matches(SCHEDULER, source, SOURCE_MAX)
            || matches(EVENT_TYPE, source, SOURCE_MAX) ? source : null;
    }

    /**
     * 주문이 만든 ID(0으로 시작하지 않는 숫자 · UUID)만 그대로, 아니면 null. 주문 아웃박스 메시지 키 등.
     */
    public static String idOrNull(String id) {
        return matches(NUMBER_ID, id, 19) || matches(UUID, id, 36) ? id : null;
    }

    /**
     * UUID만 그대로, 아니면 null(받은 메시지의 eventId).
     */
    public static String uuidOrNull(String id) {
        return matches(UUID, id, 36) ? id : null;
    }

    /**
     * 받은 메시지의 키 · aggregateId · correlationId: 파싱한 관련 주문 ID와 같거나 UUID일 때만 그대로, 아니면 null
     * (주문을 모르면 UUID만). 다른 숫자(주민번호 · 전화번호 형태 등)는 내보내지 않는다.
     */
    public static String orderRefOrNull(String value, Long orderId) {
        if (value == null) {
            return null;
        }
        if (orderId != null && value.equals(String.valueOf(orderId))) {
            return value;
        }
        return uuidOrNull(value);
    }

    /**
     * 받은 메시지의 producer: 원래 토픽 kidly.{x}.events의 x와 같을 때만 그대로, 아니면(토픽이 그 형식이 아니어도) null.
     */
    public static String receivedProducerOrNull(String producer, String sourceTopic) {
        String topicDomain = topicDomain(sourceTopic);
        return topicDomain != null && topicDomain.equals(producer) ? producer : null;
    }

    /**
     * 받은 메시지의 eventType: 이벤트 타입 형식이고 앞 마디가 원래 토픽에 허용한 값일 때만 그대로, 아니면 null.
     * 허용 값은 토픽별 목록(RECEIVED_EVENT_PREFIXES)이고, 없으면 kidly.{x}.events의 x 하나다. 토픽이 그 형식이 아니면 null.
     */
    public static String receivedEventTypeOrNull(String eventType, String sourceTopic) {
        String topicDomain = topicDomain(sourceTopic);
        if (topicDomain == null || !matches(EVENT_TYPE, eventType, SOURCE_MAX)) {
            return null;
        }
        Set<String> prefixes = RECEIVED_EVENT_PREFIXES.getOrDefault(sourceTopic, Set.of(topicDomain + "."));
        return prefixes.stream().anyMatch(eventType::startsWith) ? eventType : null;
    }

    private static String topicDomain(String sourceTopic) {
        if (sourceTopic == null) {
            return null;
        }
        Matcher matcher = DOMAIN_TOPIC.matcher(sourceTopic);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static boolean matches(Pattern pattern, String value, int max) {
        return value != null && !value.isEmpty() && value.length() <= max && pattern.matcher(value).matches();
    }
}
