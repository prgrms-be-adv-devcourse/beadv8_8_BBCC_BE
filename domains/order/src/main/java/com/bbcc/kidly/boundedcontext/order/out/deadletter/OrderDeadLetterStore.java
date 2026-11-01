package com.bbcc.kidly.boundedcontext.order.out.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.out.persistence.JpaOrderDeadLetterRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * 주문 DLT 메시지 저장소 포트 구현(#642). 받은 값은 저장할 모양(칸 길이 · NUL 문자)으로 맞춰 한 문장으로 넣고, 관리자 처리 결과는
 * 같은 트랜잭션에서 잠가 읽어 둔 행을 id로 다시 찾아(영속성 컨텍스트, 조회 없음) 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class OrderDeadLetterStore implements OrderDeadLetterRepository {

    private final JpaOrderDeadLetterRepository jpaRepository;

    @Override
    public void saveReceived(Received letter) {
        jpaRepository.saveReceived(ReceivedDeadLetter.from(letter));
    }

    @Override
    public Optional<DeadLetter> findById(Long id) {
        return jpaRepository.findById(id).map(OrderDeadLetterStore::toDeadLetter);
    }

    @Override
    public Optional<DeadLetter> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(OrderDeadLetterStore::toDeadLetter);
    }

    @Override
    public Optional<DeadLetter> findByIdForUpdateNoWait(Long id) {
        return jpaRepository.findByIdForUpdateNoWait(id).map(OrderDeadLetterStore::toDeadLetter);
    }

    @Override
    public Page<DeadLetter> findPage(Status status, FailureType failureType, String eventType, Pageable pageable) {
        return jpaRepository.findPage(status, failureType, eventType, pageable).map(OrderDeadLetterStore::toDeadLetter);
    }

    @Override
    public long countPage(Status status, FailureType failureType, String eventType) {
        return jpaRepository.countPage(status, failureType, eventType);
    }

    @Override
    public void saveHandling(Long id, Handling handling) {
        jpaRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("DLT 메시지가 없습니다: id=" + id))
            .applyHandling(handling);
    }

    private static DeadLetter toDeadLetter(OrderDeadLetter letter) {
        return new DeadLetter(letter.getId(), letter.getSourceTopic(), letter.getSourcePartition(),
            letter.getSourceOffset(), letter.getMessageKey(), letter.getEventType(), letter.getEventId(),
            letter.getPayload(), letter.getProducer(), letter.getOccurredAt(), letter.getAggregateId(),
            letter.getCorrelationId(), letter.getOrderId(), letter.getFailureType(), letter.getExceptionClass(),
            letter.getExceptionMessage(), letter.getStackTraceHead(), letter.getFirstReceivedAt(),
            letter.getLastReceivedAt(), letter.getReceivedCount(), letter.getHandling());
    }
}
