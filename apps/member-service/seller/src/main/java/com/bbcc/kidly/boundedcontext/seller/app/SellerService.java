package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerResult;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerUpdateRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 정보 조회 · 자격 정지. 정지는 SellerChangedEvent를 발행하고, 회원의 역할은 다음 토큰 재발급부터 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public SellerResult getMine(Long sellerId) {
        return SellerResult.from(sellerRepository.getById(sellerId));
    }

    @Transactional
    public SellerResult changeBusinessName(Long sellerId, SellerUpdateRequest request) {
        var seller = sellerRepository.getById(sellerId);
        if (seller.changeBusinessName(request.businessName())) {
            eventPublisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
                new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        }
        return SellerResult.from(seller);
    }

    @Transactional
    public SellerResult suspend(Long sellerId, Long adminId, SellerSuspendRequest request) {
        var now = LocalDateTime.now();
        var seller = sellerRepository.getById(sellerId);
        seller.suspend(adminId, request.reason(), now);
        eventPublisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), now,
            new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        log.info("판매자 정지: sellerId={} adminId={}", sellerId, adminId);
        return SellerResult.from(seller);
    }

    @Transactional
    public void closeIfOpen(Long sellerId) {
        sellerRepository.findById(sellerId)
            .filter(seller -> seller.getStatus() != SellerStatus.CLOSED)
            .ifPresent(this::closeAndPublish);
    }

    private void closeAndPublish(Seller seller) {
        seller.close();
        eventPublisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        log.info("판매자 탈퇴로 닫음: sellerId={}", seller.getId());
    }
}
