package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자마다 지금 상태로 SellerChangedEvent를 다시 보낸다(#643). 판매자는 바꾸지 않는다.
 * 이벤트를 놓친 쪽(새로 붙은 검색 색인 등)이 따라잡게 한다. 받는 쪽은 같은 값으로 덮어쓰므로 여러 번 불러도 결과가 같다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerChangedRepublishService {

    private final SellerRepository sellerRepository;
    private final EventPublisher eventPublisher;

    // 활성 · 정지 판매자 모두 보낸다. 받는 쪽 복사본이 정지 상태까지 맞아야 한다
    // Kafka로는 이 트랜잭션이 커밋된 뒤 나간다(SellerEventRelay)
    @Transactional
    public int republishAll() {
        int publishedCount = 0;
        for (Seller seller : sellerRepository.findAll()) {
            eventPublisher.publish(new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
            publishedCount++;
        }
        log.info("판매자 변경 이벤트 다시 발행: publishedCount={}", publishedCount);
        return publishedCount;
    }
}
