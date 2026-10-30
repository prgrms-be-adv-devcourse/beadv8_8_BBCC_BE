package com.bbcc.kidly.boundedcontext.payment.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentOutboxRepository {

    PaymentOutboxMessage save(PaymentOutboxMessage message);

    /**
     * 보내지 않은 메시지(시도 0)로 저장한다. 같은 eventId가 이미 있으면(동시에 저장한 경우 포함) 아무것도 하지 않는다.
     * 같은 이벤트를 다시 발행해도 메시지는 하나이고, 업무 트랜잭션을 실패시키지 않는다.
     */
    void saveIfAbsent(UUID eventId, String eventType, String topic, String messageKey, String payload,
        LocalDateTime now);

    /**
     * 릴레이 잠금을 기다리지 않고 얻는다. 얻으면 true, 다른 트랜잭션이 쥐고 있으면 바로 false.
     * 잠금은 지금 트랜잭션이 끝날 때 풀리므로 트랜잭션 안에서 부르고, 보내기 · 기록을 같은 트랜잭션에서 한다
     * (서버가 여러 대여도 한 번에 하나의 릴레이만 돌아 순서가 지켜진다).
     */
    boolean tryLockRelay();

    /**
     * 아직 보내지 않은 메시지를 id 순서로 최대 limit건.
     */
    List<PaymentOutboxMessage> findUnsent(int limit);

    /**
     * 보낸 시각이 cutoff보다 이른 메시지를 지운다. 안 보낸 메시지는 지우지 않는다.
     *
     * @return 지운 건수
     */
    int deleteSentBefore(LocalDateTime cutoff);
}
