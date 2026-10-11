package com.bbcc.kidly.shared.event;

import java.time.Instant;

/**
 * 도메인 사이에 주고받는 이벤트. 개별 이벤트는 이 패키지에 이 인터페이스를 구현하는 record로 추가한다.
 * 엔티티·도메인 타입 대신 ID와 그 시점의 값(원시 타입, String, Instant 등)만 담는다.
 */
public interface DomainEvent {

    Instant occurredAt();
}
