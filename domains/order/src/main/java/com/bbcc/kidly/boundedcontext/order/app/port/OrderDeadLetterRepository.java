package com.bbcc.kidly.boundedcontext.order.app.port;

import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.ReceivedDeadLetter;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 DLT 메시지 저장소(#601). 구현은 out/persistence (Spring Data가 만든다).
 */
public interface OrderDeadLetterRepository {

    /**
     * 받은 메시지를 원래 위치(토픽 · 파티션 · 오프셋)당 1건으로 저장한다. 처음이면 OPEN · 받은 횟수 1로 넣고, 이미 있으면
     * 받은 횟수 +1 · 마지막 받은 시각만 바꾼다(처음 값 · 상태 · 처리 칸은 그대로). 한 문장이라 동시에 와도 1건이다.
     */
    void saveReceived(ReceivedDeadLetter letter);

    Optional<OrderDeadLetter> findById(Long id);

    /**
     * 상태가 맞는 메시지. 실패 유형 · eventType은 null이면 거르지 않는다. 최근 받은 것부터(같으면 ID 큰 것부터).
     */
    Page<OrderDeadLetter> findPage(OrderDeadLetter.Status status, OrderDeadLetter.FailureType failureType,
        String eventType, Pageable pageable);

    /**
     * findPage와 같은 조건의 건수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countPage(OrderDeadLetter.Status status, OrderDeadLetter.FailureType failureType, String eventType);
}
