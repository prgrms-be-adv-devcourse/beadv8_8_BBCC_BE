package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import java.time.LocalDateTime;

/**
 * DLT 본문에서 관리자 확인용 칸을 읽고, 수신 예외 이름으로 실패 유형을 정한다.
 * 읽을 수 없는 본문도 실패시키지 않고 알 수 없는 칸은 null로 돌려준다.
 */
public interface DeadLetterInspection {

    Fields read(String payload, String eventType, String key);

    FailureType failureType(String exceptionClass);

    record Fields(String producer, LocalDateTime occurredAt, String aggregateId, String correlationId, Long orderId) {
    }
}
