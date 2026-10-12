package com.bbcc.kidly.shared.event;

import java.time.LocalDateTime;

/**
 * 도메인 간 이벤트. 모든 이벤트 record가 구현한다.
 * 복사본을 갱신하는 쪽은 저장된 값보다 {@link #occurredAt()}이 늦은 이벤트만 반영한다.
 */
public interface DomainEvent {

    /**
     * 이벤트가 발생한 시각. 발행하는 쪽이 변경을 저장할 때 쓴 시각을 넣는다.
     */
    LocalDateTime occurredAt();
}
