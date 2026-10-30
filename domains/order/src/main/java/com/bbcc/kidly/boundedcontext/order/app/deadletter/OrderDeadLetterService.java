package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterSummary;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.OrderDeadLetter;
import com.bbcc.kidly.boundedcontext.order.domain.deadletter.ReceivedDeadLetter;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 DLT 메시지(#601). 재시도해도 계속 실패한 메시지를 저장하고 관리자가 확인한다.
 * 관리자 확인 대상 원칙: 기본 목록은 사람이 조치할 OPEN만, 처리 · 종료된 건은 상태로 걸러 보는 이력이다.
 */
@Service
@RequiredArgsConstructor
public class OrderDeadLetterService {

    static final String ACTION_READ_FAILED =
        "보낸 도메인의 메시지 형식 · 값이 계약과 다름. 보낸 쪽 확인 뒤 재처리(고치기 전 재처리는 다시 실패)";
    static final String ACTION_STORE_FAILED = "DB 상태 확인 뒤 재처리";
    static final String ACTION_PAYMENT_COMPLETED_STORE_FAILED = "결제는 됐는데 주문이 결제대기일 수 있음 — 우선 재처리";
    static final String ACTION_PROCESS_FAILED = "주문 현재 상태 확인 뒤 재처리 또는 종료";

    private static final String PAYMENT_COMPLETED = "payment.completed";

    private final OrderDeadLetterRepository deadLetterRepository;
    private final OrderRepository orderRepository;
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

    /**
     * 최근 받은 것부터. statusName이 null이면 OPEN(확인 대상)만. 실패 유형 · eventType은 null이면 거르지 않는다.
     */
    @Transactional(readOnly = true)
    public Page<OrderDeadLetterSummary> getDeadLetters(String statusName, String failureTypeName, String eventType,
        int page, int size) {
        OrderDeadLetter.Status status = statusName == null ? OrderDeadLetter.Status.OPEN : toStatus(statusName);
        OrderDeadLetter.FailureType failureType = failureTypeName == null ? null : toFailureType(failureTypeName);
        PageRequest pageable = PageRequest.of(page, size);
        // 저장소는 offset(page × size)이 int를 넘으면 조회하지 못하므로(500), 조회 없이 빈 페이지로 답한다
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, deadLetterRepository.countPage(status, failureType, eventType));
        }
        return deadLetterRepository.findPage(status, failureType, eventType, pageable)
            .map(OrderDeadLetterSummary::from);
    }

    /**
     * 저장한 칸 전체 + 관련 주문의 지금 상태(저장하지 않음) + 권장 조치. 없으면 404.
     */
    @Transactional(readOnly = true)
    public OrderDeadLetterDetail getDeadLetter(Long deadLetterId) {
        OrderDeadLetter letter = deadLetterRepository.findById(deadLetterId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.DEAD_LETTER_NOT_FOUND));
        String orderStatus = letter.getOrderId() == null ? null
            : orderRepository.findById(letter.getOrderId()).map(order -> order.getStatus().name()).orElse(null);
        return OrderDeadLetterDetail.of(letter, orderStatus, recommendedAction(letter));
    }

    // 처리 · 종료된 건은 조치할 것이 없다. 결제 완료의 보관 실패는 돈이 빠졌는데 주문이 결제대기로 남았을 수 있어 먼저 본다(#594)
    static String recommendedAction(OrderDeadLetter letter) {
        if (letter.getStatus() != OrderDeadLetter.Status.OPEN) {
            return null;
        }
        return switch (letter.getFailureType()) {
            case READ_FAILED -> ACTION_READ_FAILED;
            case STORE_FAILED -> PAYMENT_COMPLETED.equals(letter.getEventType())
                ? ACTION_PAYMENT_COMPLETED_STORE_FAILED : ACTION_STORE_FAILED;
            case PROCESS_FAILED -> ACTION_PROCESS_FAILED;
        };
    }

    private static OrderDeadLetter.Status toStatus(String statusName) {
        try {
            return OrderDeadLetter.Status.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_DEAD_LETTER_STATUS);
        }
    }

    private static OrderDeadLetter.FailureType toFailureType(String failureTypeName) {
        try {
            return OrderDeadLetter.FailureType.valueOf(failureTypeName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_DEAD_LETTER_FAILURE_TYPE);
        }
    }
}
