package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummaryQuery;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 신청 · 조회 · 승인.
 * 승인은 한 트랜잭션에서 신청을 APPROVED로 바꾸고, 판매자를 만들고, SellerChangedEvent를 발행한다.
 */
@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerRepository sellerRepository;
    private final SellerApplicationSummaryQuery sellerApplicationSummaryQuery;
    private final EventPublisher eventPublisher;

    // 같은 회원의 동시 신청은 막지 않는다. 두 번째 승인이 이미 판매자라서 실패한다
    @Transactional
    public SellerApplicationResult submit(
        Long applicantId, String applicantNickname, SellerApplicationRequest request) {
        if (sellerRepository.existsById(applicantId)) {
            throw new BusinessException(SellerErrorCode.ALREADY_SELLER);
        }
        if (sellerApplicationRepository.existsByApplicantIdAndStatus(
            applicantId, SellerApplicationStatus.PENDING)) {
            throw new BusinessException(SellerErrorCode.APPLICATION_ALREADY_PENDING);
        }
        var application = SellerApplication.submit(request.toSubmitInfo(applicantId, applicantNickname));
        return SellerApplicationResult.from(sellerApplicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public SellerApplicationResult getLatest(Long applicantId) {
        return sellerApplicationRepository.findFirstByApplicantIdOrderByIdDesc(applicantId)
            .map(SellerApplicationResult::from)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<SellerApplicationSummary> getApplications(SellerApplicationStatus status, Pageable pageable) {
        return sellerApplicationSummaryQuery.findAll(status, pageable);
    }

    // 정말 동시에 같은 회원의 신청 두 건을 승인하면 Seller PK 충돌로 실패한다(500). 판매자는 1명만 남는다
    @Transactional
    public SellerApplicationSummary approve(Long applicationId, Long adminId) {
        var now = LocalDateTime.now();
        var application = sellerApplicationRepository.getById(applicationId);
        application.approve(adminId, now);
        if (sellerRepository.existsById(application.getApplicantId())) {
            throw new BusinessException(SellerErrorCode.ALREADY_SELLER);
        }
        var seller = sellerRepository.save(Seller.from(application));
        eventPublisher.publish(new SellerChangedEvent(UUID.randomUUID(), now,
            new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        return SellerApplicationSummary.from(application);
    }
}
