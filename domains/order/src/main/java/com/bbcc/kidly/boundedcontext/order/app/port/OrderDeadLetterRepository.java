package com.bbcc.kidly.boundedcontext.order.app.port;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 DLT 메시지 저장소(#601). 구현은 out/deadletter(OrderDeadLetterStore). 보관 행(엔티티)은 Kafka 실패 처리 장치라 out에 있고, app은 이 포트의 값으로
 * 읽고 관리자 처리 결과({@link Handling})만 되돌려 쓴다(#642). 부르는 쪽 트랜잭션에 합류한다.
 */
public interface OrderDeadLetterRepository {

    /**
     * 받은 메시지를 원래 위치(토픽 · 파티션 · 오프셋)당 1건으로 저장한다. 처음이면 OPEN · 받은 횟수 1로 넣고, 이미 있으면
     * 받은 횟수 +1 · 마지막 받은 시각만 바꾼다(처음 값 · 상태 · 처리 칸은 그대로). 한 문장이라 동시에 와도 1건이다.
     * 칸 길이를 넘는 값은 잘라 저장한다(DLT 저장이 길이 때문에 실패하지 않게).
     */
    void saveReceived(Received letter);

    Optional<DeadLetter> findById(Long id);

    /**
     * 행 쓰기 잠금으로 읽는다(종료). 재처리 중이면 끝날 때까지 기다렸다가 최신 행을 본다.
     */
    Optional<DeadLetter> findByIdForUpdate(Long id);

    /**
     * 기다리지 않는 행 쓰기 잠금으로 읽는다(재처리). 다른 재처리 · 종료가 잠그고 있으면 바로 실패한다(같은 메시지는 한 번만 재처리).
     */
    Optional<DeadLetter> findByIdForUpdateNoWait(Long id);

    /**
     * 상태가 맞는 메시지. 실패 유형 · eventType은 null이면 거르지 않는다. 결제 토픽에서 온 것이 먼저(#632), 그 안과 나머지는 최근 받은 것부터(같으면 ID 큰 것부터).
     */
    Page<DeadLetter> findPage(Status status, FailureType failureType, String eventType, Pageable pageable);

    /**
     * findPage와 같은 조건의 건수. 목록을 읽을 수 없는 큰 page의 빈 페이지에 전체 건수를 채운다.
     */
    long countPage(Status status, FailureType failureType, String eventType);

    /**
     * 관리자 처리 결과를 반영한다. 같은 트랜잭션에서 잠가 읽은 행에 쓰고 커밋 때 함께 반영된다(받은 값 칸은 바꾸지 않음).
     */
    void saveHandling(Long id, Handling handling);

    /**
     * 왜 DLT로 갔는지. 관리자 권장 조치가 유형마다 다르다.
     */
    enum FailureType {
        /** 읽기 실패: 보낸 도메인의 메시지 형식 · 값이 계약과 다름(모르는 enum 값 · 깨진 본문 · eventType 헤더 없음) */
        READ_FAILED,
        /** 보관 실패: DB · 트랜잭션 오류로 처리도 주문 자체 보관도 못 함 */
        STORE_FAILED,
        /** 그 밖의 처리 실패: 업무 규칙 · 상태 등 다시 해도 같은 결과인 예외 */
        PROCESS_FAILED
    }

    enum Status {
        OPEN,
        REPROCESSED,
        CLOSED
    }

    /**
     * DLT로 받은 메시지 한 번(#601).
     *
     * @param sourceTopic 원래 토픽(DLT 헤더). 원래 위치(토픽 · 파티션 · 오프셋)당 1건
     * @param payload 원본 본문 그대로
     * @param producer 본문 공통 칸에서 꺼낸 보낸 도메인. 본문을 읽지 못하면 null(occurredAt · aggregateId · correlationId도 같음)
     * @param orderId 관련 주문 ID. 주문을 다루는 이벤트(결제 · 재고)에서 알 수 있을 때만
     * @param stackTrace 스택(저장은 앞부분만)
     * @param receivedAt 받은 시각(처음이면 처음 · 마지막 받은 시각, 다시 받음이면 마지막 받은 시각)
     */
    record Received(
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String messageKey,
        String eventType,
        String eventId,
        String payload,
        String producer,
        LocalDateTime occurredAt,
        String aggregateId,
        String correlationId,
        Long orderId,
        FailureType failureType,
        String exceptionClass,
        String exceptionMessage,
        String stackTrace,
        LocalDateTime receivedAt) {
    }

    /**
     * 저장된 DLT 메시지 한 건. 받은 값과 관리자 처리 칸({@link Handling})으로 나뉜다.
     */
    record DeadLetter(
        Long id,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String messageKey,
        String eventType,
        String eventId,
        String payload,
        String producer,
        LocalDateTime occurredAt,
        String aggregateId,
        String correlationId,
        Long orderId,
        FailureType failureType,
        String exceptionClass,
        String exceptionMessage,
        String stackTraceHead,
        LocalDateTime firstReceivedAt,
        LocalDateTime lastReceivedAt,
        int receivedCount,
        Handling handling) {

        public Status status() {
            return handling.status();
        }

        public DeadLetter withHandling(Handling changed) {
            return new DeadLetter(id, sourceTopic, sourcePartition, sourceOffset, messageKey, eventType, eventId,
                payload, producer, occurredAt, aggregateId, correlationId, orderId, failureType, exceptionClass,
                exceptionMessage, stackTraceHead, firstReceivedAt, lastReceivedAt, receivedCount, changed);
        }
    }

    /**
     * 관리자 처리 칸.
     *
     * @param lastReprocessResult 마지막 재처리 실패 이유 또는 넘김 표시. 처리 성공이면 null
     */
    record Handling(
        Status status,
        Long handledBy,
        LocalDateTime handledAt,
        String memo,
        int reprocessCount,
        LocalDateTime lastReprocessedAt,
        String lastReprocessResult) {
    }
}
