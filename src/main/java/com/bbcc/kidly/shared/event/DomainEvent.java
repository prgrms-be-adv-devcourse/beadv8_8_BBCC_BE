package com.bbcc.kidly.shared.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 도메인 간 이벤트. 모든 이벤트 record가 구현한다.
 * 기획서 6.2의 공통 형식 중 data를 뺀 칸이고, data는 record의 나머지 필드다.
 * record는 {@link #eventId()}, {@link #occurredAt()}을 필드로 갖고 {@link #aggregateId()}를 구현한다. 나머지는 기본값을 쓴다.
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
        return EventNames.typeOf(getClass());
    }

    /**
     * 발행한 도메인. shared.{도메인}.event 패키지에서 만든다.
     */
    default String producer() {
        return EventNames.producerOf(getClass().getPackageName());
    }
}
