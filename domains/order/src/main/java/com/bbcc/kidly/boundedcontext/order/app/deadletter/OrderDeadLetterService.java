package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.ReceivedDeadLetter;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 DLT 메시지(#601). 재시도해도 계속 실패한 메시지를 관리자가 확인할 수 있게 저장한다.
 */
@Service
@RequiredArgsConstructor
public class OrderDeadLetterService {

    private final OrderDeadLetterRepository deadLetterRepository;
    private final DeadLetterPayloads payloads;

    /**
     * DLT로 온 메시지를 원래 위치당 1건으로 저장한다. 같은 메시지가 다시 오면 받은 횟수 · 마지막 받은 시각만 바뀐다.
     * 실패 유형 · 본문 칸은 처음 받은 값 그대로다. 저장이 실패하면 예외를 그대로 던진다(받는 쪽이 재시도한다).
     */
    @Transactional
    public void receive(ReceiveDeadLetterCommand command) {
        DeadLetterPayloads.Fields fields = payloads.read(command.payload(), command.eventType(),
            command.messageKey());
        deadLetterRepository.saveReceived(new ReceivedDeadLetter(command.sourceTopic(), command.sourcePartition(),
            command.sourceOffset(), command.messageKey(), command.eventType(), command.eventId(), command.payload(),
            fields.producer(), fields.occurredAt(), fields.aggregateId(), fields.correlationId(), fields.orderId(),
            DeadLetterFailureTypes.of(command.exceptionClass()), command.exceptionClass(),
            command.exceptionMessage(), command.stackTrace(), LocalDateTime.now()));
    }
}
