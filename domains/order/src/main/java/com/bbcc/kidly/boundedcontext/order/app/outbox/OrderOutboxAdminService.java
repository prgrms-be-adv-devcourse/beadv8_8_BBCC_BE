package com.bbcc.kidly.boundedcontext.order.app.outbox;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderOutboxRepository.OutboxStatus;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 격리 원인을 확인한 뒤 같은 이벤트를 다시 전송 대기시킨다. 원본 변경 · 폐기 · 보냄 처리는 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderOutboxAdminService {

    private final OrderOutboxRepository repository;

    public Page<OutboxStatus> findQuarantined(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, repository.countQuarantined());
        }
        return repository.findQuarantined(pageable);
    }

    public OutboxStatus get(Long id) {
        return repository.findStatus(id).orElseThrow(() -> new BusinessException(OrderErrorCode.OUTBOX_NOT_FOUND));
    }

    @Transactional
    public OutboxStatus retry(Long id, Long adminId) {
        if (!repository.lockById(id)) {
            throw new BusinessException(OrderErrorCode.OUTBOX_NOT_FOUND);
        }
        OutboxStatus before = get(id);
        if (before.getSentAt() != null) {
            throw new BusinessException(OrderErrorCode.OUTBOX_ALREADY_SENT);
        }
        if (before.getQuarantinedAt() == null) {
            return before;
        }
        repository.retryQuarantined(id, adminId, LocalDateTime.now());
        log.info("아웃박스 격리 재시도 대기: outboxId={} eventId={} eventType={} adminId={}",
            id, before.getEventId(), before.getEventType(), adminId);
        return get(id);
    }
}
