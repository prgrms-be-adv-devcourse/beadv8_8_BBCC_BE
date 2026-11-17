package com.bbcc.kidly.boundedcontext.payment.app;

import com.bbcc.kidly.boundedcontext.payment.app.dto.ParkedOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentErrorCode;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxRepository;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이가 격리한 결제 아웃박스 메시지를 관리자가 보고 다시 보내게 한다(#1389).
 * 원인(예: 브로커가 받지 않는 크기)을 고친 뒤 다시 보낸다. 풀면 다음 릴레이가 그 메시지부터 같은 키의 뒤 메시지까지 순서대로 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxParkingService {

    private final PaymentOutboxRepository outboxRepository;

    @Transactional(readOnly = true)
    public Page<ParkedOutboxMessage> getParked(int page, int size) {
        return outboxRepository.findParked(PageRequest.of(page, size)).map(ParkedOutboxMessage::from);
    }

    /**
     * @param releasedBy 다시 보내기로 바꾼 관리자 ID
     */
    @Transactional
    public ParkedOutboxMessage release(Long outboxId, Long releasedBy) {
        PaymentOutboxMessage message = outboxRepository.findById(outboxId)
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.OUTBOX_MESSAGE_NOT_FOUND));
        ParkedOutboxMessage parked = ParkedOutboxMessage.from(message);
        if (!message.release()) {
            throw new BusinessException(PaymentErrorCode.OUTBOX_MESSAGE_NOT_PARKED);
        }
        log.info("격리된 결제 아웃박스 메시지를 다시 보내기로 바꿉니다. outboxId={} eventType={} eventId={} key={} releasedBy={}",
            message.getId(), message.getEventType(), message.getEventId(), message.getMessageKey(), releasedBy);
        return parked;
    }
}
