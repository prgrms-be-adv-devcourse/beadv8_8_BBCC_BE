package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerResult;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerUpdateRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            eventPublisher.publishEvent(seller.toChangedEvent(LocalDateTime.now()));
        }
        return SellerResult.from(seller);
    }

    @Transactional
    public SellerResult suspend(Long sellerId, Long adminId, SellerSuspendRequest request) {
        var now = LocalDateTime.now();
        var seller = sellerRepository.getById(sellerId);
        seller.suspend(adminId, request.reason(), now);
        eventPublisher.publishEvent(seller.toChangedEvent(now));
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
        eventPublisher.publishEvent(seller.toChangedEvent(LocalDateTime.now()));
        log.info("판매자 탈퇴로 닫음: sellerId={}", seller.getId());
    }
}
