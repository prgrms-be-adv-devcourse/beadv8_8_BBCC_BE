package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerChangedRepublishService {

    private final SellerRepository sellerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int republishAll() {
        var sellers = sellerRepository.findAll();
        for (var seller : sellers) {
            eventPublisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        }
        log.info("판매자 변경 이벤트 다시 발행: publishedCount={}", sellers.size());
        return sellers.size();
    }
}
