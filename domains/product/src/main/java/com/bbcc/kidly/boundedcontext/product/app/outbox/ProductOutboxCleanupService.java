package com.bbcc.kidly.boundedcontext.product.app.outbox;

import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxCleanupService {

    private final ProductOutboxRepository outboxRepository;
    private final ProductOutboxProperties properties;

    @Transactional
    public void cleanUp() {
        int deleted = outboxRepository.deleteSentBefore(LocalDateTime.now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("보낸 상품 아웃박스 메시지를 지웠습니다. count={}", deleted);
        }
    }
}
