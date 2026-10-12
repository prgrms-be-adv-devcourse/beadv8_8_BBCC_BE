package com.bbcc.kidly.shared.event;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 도메인 간 이벤트. 모든 이벤트 record가 구현한다.
 * 기획서 6.2의 공통 형식이고, 이벤트마다 다른 내용은 {@link #data()}에 담는다.
 * record는 {@link #eventId()}, {@link #occurredAt()}, {@link #data()}를 필드로 갖고 {@link #aggregateId()}를 구현한다.
 * 나머지는 기본값을 쓴다.
 * 복사본을 갱신하는 쪽은 저장된 값보다 {@link #occurredAt()}이 늦은 이벤트만 반영한다.
 */
public interface DomainEvent {

    /**
     * 이벤트 하나를 구분하는 값. 발행하는 쪽이 {@code UUID.randomUUID()}로 넣는다.
     */
    UUID eventId();

    /**
     * 이벤트가 발생한 시각. 발행하는 쪽이 변경을 저장할 때 쓴 시각을 넣는다.
     */
    LocalDateTime occurredAt();

    /**
     * 이벤트마다 다른 내용. 이벤트 record 안에 {@code Data} record로 두고, 엔티티 대신 ID와 그 시점의 값만 담는다.
     * record의 접근자가 {@code Data}를 돌려주므로 받는 쪽은 형변환 없이 쓴다.
     */
    Object data();

    /**
     * 이벤트가 다루는 대상의 ID. 예: 주문 이벤트는 orderId, 자녀 정보 변경은 childId.
     */
    String aggregateId();

    /**
     * 같은 흐름에 속한 이벤트를 묶는 값. 기본값은 {@link #aggregateId()}.
     */
    default String correlationId() {
        return aggregateId();
    }

    /**
     * 필드 구성의 버전. 필드는 추가만 하므로 바꿀 일이 생길 때만 재정의한다.
     */
    default int schemaVersion() {
        return 1;
    }

    /**
     * {대상}.{과거형}. 클래스 이름에서 만든다(OrderCreatedEvent → order.created).
     * 대상이 두 단어 이상이면 재정의한다(예: order-item.purchase-confirmed).
     */
    default String eventType() {
        return typeOf(getClass().getSimpleName());
    }

    /**
     * 발행한 도메인. shared.{도메인}.event 패키지에서 만든다.
     */
    default String producer() {
        return producerOf(getClass());
    }

    private static String typeOf(String className) {
        String name = className.endsWith("Event")
            ? className.substring(0, className.length() - "Event".length())
            : className;
        String[] words = name.split("(?=[A-Z])");
        String target = words[0].toLowerCase(Locale.ROOT);
        String pastTense = Arrays.stream(words, 1, words.length)
            .map(word -> word.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));
        return pastTense.isEmpty() ? target : target + "." + pastTense;
    }

    private static String producerOf(Class<?> eventClass) {
        String[] parts = eventClass.getPackageName().split("\\.");
        int last = parts.length - 1;
        if (last < 2 || !parts[last - 2].equals("shared") || !parts[last].equals("event")) {
            throw new IllegalStateException(
                "producer를 정하지 못했습니다. 도메인 이벤트는 shared.{도메인}.event 패키지에 둡니다: " + eventClass.getName());
        }
        return parts[last - 1];
    }
}
