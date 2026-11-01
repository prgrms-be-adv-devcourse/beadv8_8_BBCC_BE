package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.DeadLetter;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Handling;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.Status;
import java.time.LocalDateTime;

/**
 * DLT 메시지 관리자 처리 규칙(#601, #642). 보관 행은 out에 있고, 이 규칙이 다음 처리 칸을 정해 저장소에 되돌려 쓴다.
 * OPEN만 처리할 수 있다(서비스가 먼저 거른다).
 */
public final class DeadLetterHandling {

    /** 재처리 결과 칸 길이 */
    static final int RESULT_LENGTH = 500;
    static final String HANDED_OFF_RESULT = "자체 보관으로 넘김(재처리 대기 목록에서 처리)";
    /** 결제 토픽. 여기서 온 메시지는 돈이 걸려 있어 관리자 목록 맨 앞에 둔다(#632) */
    static final String PAYMENT_SOURCE_TOPIC = "kidly.payment.events";

    private DeadLetterHandling() {
    }

    /**
     * 재처리로 처리를 마쳤다. 처리자 · 시각을 남기고 마지막 실패 이유는 지운다.
     */
    static Handling reprocessed(DeadLetter letter, Long adminId, LocalDateTime now) {
        Handling handling = requireOpen(letter);
        return new Handling(Status.REPROCESSED, adminId, now, handling.memo(), handling.reprocessCount() + 1, now,
            null);
    }

    /**
     * 재처리에서 원래 처리가 실패를 주문 자체 보관(결제 반영 실패 · 이벤트 처리 실패 기록)에 넘겼다. 이 메시지로 할 일은 끝나
     * REPROCESSED지만, 진짜 처리 성공과 구분되게 결과에 넘김을 표시한다(이후는 그 재처리 대기 목록에서).
     */
    static Handling handedOff(DeadLetter letter, Long adminId, LocalDateTime now) {
        Handling handling = reprocessed(letter, adminId, now);
        return new Handling(handling.status(), handling.handledBy(), handling.handledAt(), handling.memo(),
            handling.reprocessCount(), handling.lastReprocessedAt(), HANDED_OFF_RESULT);
    }

    /**
     * 재처리가 실패했다. OPEN 그대로 두고 시도 횟수 · 시각 · 실패 이유(본문 없는 값, {@value #RESULT_LENGTH}자까지)만 남긴다.
     */
    static Handling reprocessFailed(DeadLetter letter, String reason, LocalDateTime now) {
        Handling handling = requireOpen(letter);
        String result = reason == null || reason.length() <= RESULT_LENGTH
            ? reason : reason.substring(0, RESULT_LENGTH);
        return new Handling(handling.status(), handling.handledBy(), handling.handledAt(), handling.memo(),
            handling.reprocessCount() + 1, now, result);
    }

    /**
     * 확인 후 종료한다(처리하지 않음). 메모는 서비스 입력 검증이 비어 있지 않게 한다.
     */
    static Handling closed(DeadLetter letter, Long adminId, String memo, LocalDateTime now) {
        Handling handling = requireOpen(letter);
        return new Handling(Status.CLOSED, adminId, now, memo, handling.reprocessCount(), handling.lastReprocessedAt(),
            handling.lastReprocessResult());
    }

    /**
     * 결제 토픽에서 온 메시지인지(#632). 결제 완료가 빠지면 돈은 빠졌는데 주문이 결제대기 · 만료 취소될 수 있어 먼저 본다.
     */
    public static boolean isPaymentEvent(DeadLetter letter) {
        return PAYMENT_SOURCE_TOPIC.equals(letter.sourceTopic());
    }

    private static Handling requireOpen(DeadLetter letter) {
        if (letter.status() != Status.OPEN) {
            throw new IllegalStateException("OPEN인 DLT 메시지만 처리할 수 있습니다: status=" + letter.status());
        }
        return letter.handling();
    }
}
