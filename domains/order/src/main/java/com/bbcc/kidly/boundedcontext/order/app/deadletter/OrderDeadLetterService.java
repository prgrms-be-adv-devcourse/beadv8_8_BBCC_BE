package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.DeadLetterReplay;
import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterDetail;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.OrderDeadLetterSummary;
import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.DeadLetterReplayer;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.DeadLetter;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Handling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Received;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Status;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 DLT 메시지(#601). 재시도해도 계속 실패한 메시지를 저장하고 관리자가 확인한다.
 * 관리자 확인 대상 원칙: 기본 목록은 사람이 조치할 OPEN만, 처리 · 종료된 건은 상태로 걸러 보는 이력이다.
 */
@Slf4j
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
    private final DeadLetterReplayer replayer;
    private final PlatformTransactionManager transactionManager;

    /**
     * DLT로 온 메시지를 원래 위치당 1건으로 저장한다. 같은 메시지가 다시 오면 받은 횟수 · 마지막 받은 시각만 바뀐다.
     * 실패 유형 · 본문 칸은 처음 받은 값 그대로다. 저장이 실패하면 예외를 그대로 던진다(받는 쪽이 재시도한다).
     */
    @Transactional
    public void receive(ReceiveDeadLetterCommand command) {
        DeadLetterPayloads.Fields fields = payloads.read(command.payload(), command.eventType(),
            command.messageKey());
        deadLetterRepository.saveReceived(new Received(command.sourceTopic(), command.sourcePartition(),
            command.sourceOffset(), command.messageKey(), command.eventType(), command.eventId(), command.payload(),
            fields.producer(), fields.occurredAt(), fields.aggregateId(), fields.correlationId(), fields.orderId(),
            DeadLetterFailureTypes.of(command.exceptionClass()), command.exceptionClass(),
            command.exceptionMessage(), command.stackTrace(), LocalDateTime.now()));
    }

    /**
     * 결제 토픽에서 온 것이 먼저(#632), 그 안과 나머지는 최근 받은 것부터. statusName이 null이면 OPEN(확인 대상)만. 실패 유형 · eventType은 null이면 거르지 않는다.
     */
    @Transactional(readOnly = true)
    public Page<OrderDeadLetterSummary> getDeadLetters(String statusName, String failureTypeName, String eventType,
        int page, int size) {
        Status status = statusName == null ? Status.OPEN : toStatus(statusName);
        FailureType failureType = failureTypeName == null ? null : toFailureType(failureTypeName);
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
        DeadLetter letter = deadLetterRepository.findById(deadLetterId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.DEAD_LETTER_NOT_FOUND));
        return detailOf(letter);
    }

    /**
     * 저장한 본문 · 헤더로 원래 Kafka 수신 처리를 다시 실행한다(원래 토픽에 다시 넣지 않는다). 성공하면 REPROCESSED(처리자 · 시각),
     * 실패하면 OPEN 그대로 재처리 횟수 · 시각 · 실패 이유(예외 종류, 본문 없음)를 남기고 200으로 돌려준다.
     * 이미 처리된 이벤트면 원래 처리의 멱등 처리로 결과 1회분이다.
     * <ul>
     *   <li>같은 메시지는 한 번만: 기록을 기다리지 않는 잠금으로 잡고, 다른 재처리 · 종료가 잡고 있으면 409(재처리 중)</li>
     *   <li>원래 처리는 이 트랜잭션을 멈춘 채(잠금은 유지) 트랜잭션 없이 부른다. Kafka로 받았을 때처럼 처리 · 실패 보관이
     *       각자 트랜잭션에서 커밋해야 실패 보관이 남는다</li>
     *   <li>이미 재처리됐으면 아무것도 하지 않고 지금 상세(기존 재처리 대기와 같음), 종료됐으면 409</li>
     *   <li>원래 처리가 실패를 주문 자체 보관에 넘겼으면 REPROCESSED에 넘김 표시(진짜 성공과 구분)</li>
     * </ul>
     */
    @Transactional
    public OrderDeadLetterDetail reprocess(Long deadLetterId, Long adminId) {
        DeadLetter letter = lockForReprocess(deadLetterId);
        if (letter.status() == Status.REPROCESSED) {
            return detailOf(letter);
        }
        if (letter.status() == Status.CLOSED) {
            throw new BusinessException(OrderErrorCode.DEAD_LETTER_CLOSED);
        }
        ReplayAttempt attempt = replayWithoutTransaction(new DeadLetterReplay(letter.sourceTopic(),
            letter.sourcePartition(), letter.sourceOffset(), letter.messageKey(), letter.eventType(),
            letter.eventId(), letter.payload()));
        LocalDateTime now = LocalDateTime.now();
        Handling handling;
        if (attempt.failure() != null) {
            handling = DeadLetterHandling.reprocessFailed(letter, attempt.failure(), now);
        } else if (attempt.outcome() == DeadLetterReplay.Outcome.HANDED_OFF) {
            handling = DeadLetterHandling.handedOff(letter, adminId, now);
        } else {
            handling = DeadLetterHandling.reprocessed(letter, adminId, now);
        }
        deadLetterRepository.saveHandling(letter.id(), handling);
        DeadLetter handled = letter.withHandling(handling);
        log.info("DLT 메시지 재처리: deadLetterId={} eventType={} eventId={} orderId={} status={} result={}",
            handled.id(), handled.eventType(), handled.eventId(), handled.orderId(), handled.status(),
            handling.lastReprocessResult());
        return detailOf(handled);
    }

    /**
     * 확인 후 종료한다(처리하지 않음). 메모 · 처리자 · 시각을 남긴다. 이미 종료됐으면 그대로 200, 재처리됐으면 409.
     * 재처리 중이면 그 재처리가 끝날 때까지 기다린 뒤 최신 상태로 판단한다.
     */
    @Transactional
    public OrderDeadLetterDetail close(Long deadLetterId, Long adminId, String memo) {
        DeadLetter letter = deadLetterRepository.findByIdForUpdate(deadLetterId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.DEAD_LETTER_NOT_FOUND));
        if (letter.status() == Status.REPROCESSED) {
            throw new BusinessException(OrderErrorCode.DEAD_LETTER_REPROCESSED);
        }
        if (letter.status() == Status.OPEN) {
            Handling handling = DeadLetterHandling.closed(letter, adminId, memo, LocalDateTime.now());
            deadLetterRepository.saveHandling(letter.id(), handling);
            letter = letter.withHandling(handling);
            log.info("DLT 메시지 종료: deadLetterId={} eventType={} eventId={} orderId={}",
                letter.id(), letter.eventType(), letter.eventId(), letter.orderId());
        }
        return detailOf(letter);
    }

    private DeadLetter lockForReprocess(Long deadLetterId) {
        try {
            return deadLetterRepository.findByIdForUpdateNoWait(deadLetterId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.DEAD_LETTER_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(OrderErrorCode.DEAD_LETTER_IN_PROGRESS);
        }
    }

    // 실패 이유는 가장 안쪽 예외 종류(예외 메시지에는 본문 값이 들어 있을 수 있어 쓰지 않는다)
    private ReplayAttempt replayWithoutTransaction(DeadLetterReplay message) {
        var withoutTransaction = new TransactionTemplate(transactionManager);
        withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return withoutTransaction.execute(status -> {
            try {
                return new ReplayAttempt(replayer.replay(message), null);
            } catch (RuntimeException e) {
                return new ReplayAttempt(null,
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            }
        });
    }

    private record ReplayAttempt(DeadLetterReplay.Outcome outcome, String failure) {
    }

    private OrderDeadLetterDetail detailOf(DeadLetter letter) {
        String orderStatus = letter.orderId() == null ? null
            : orderRepository.findById(letter.orderId()).map(order -> order.getStatus().name()).orElse(null);
        return OrderDeadLetterDetail.of(letter, orderStatus, recommendedAction(letter));
    }

    // 처리 · 종료된 건은 조치할 것이 없다. 결제 완료의 보관 실패는 돈이 빠졌는데 주문이 결제대기로 남았을 수 있어 먼저 본다(#594)
    static String recommendedAction(DeadLetter letter) {
        if (letter.status() != Status.OPEN) {
            return null;
        }
        return switch (letter.failureType()) {
            case READ_FAILED -> ACTION_READ_FAILED;
            case STORE_FAILED -> PAYMENT_COMPLETED.equals(letter.eventType())
                ? ACTION_PAYMENT_COMPLETED_STORE_FAILED : ACTION_STORE_FAILED;
            case PROCESS_FAILED -> ACTION_PROCESS_FAILED;
        };
    }

    private static Status toStatus(String statusName) {
        try {
            return Status.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_DEAD_LETTER_STATUS);
        }
    }

    private static FailureType toFailureType(String failureTypeName) {
        try {
            return FailureType.valueOf(failureTypeName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_DEAD_LETTER_FAILURE_TYPE);
        }
    }
}
