package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRejectRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummaryQuery;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.bbcc.kidly.shared.seller.event.SellerApplicationRejectedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerRepository sellerRepository;
    private final SellerApplicationSummaryQuery sellerApplicationSummaryQuery;
    private final ApplicationEventPublisher eventPublisher;

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
        var submitInfo = request.toSubmitInfo(applicantId, applicantNickname);
        var application = sellerApplicationRepository.save(SellerApplication.submit(submitInfo));
        log.info("판매자 신청: applicationId={} applicantId={}", application.getId(), applicantId);
        return SellerApplicationResult.from(application);
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

    @Transactional
    public SellerApplicationSummary approve(Long applicationId, Long adminId) {
        var now = LocalDateTime.now();
        var application = sellerApplicationRepository.getById(applicationId);
        application.approve(adminId, now);
        if (sellerRepository.existsById(application.getApplicantId())) {
            log.warn("판매자 신청 승인 거절: applicationId={} applicantId={} reason=already-seller",
                applicationId, application.getApplicantId());
            throw new BusinessException(SellerErrorCode.ALREADY_SELLER);
        }
        var seller = sellerRepository.save(Seller.from(application));
        eventPublisher.publishEvent(new SellerChangedEvent(UUID.randomUUID(), now,
            new SellerChangedEvent.Data(seller.getId(), seller.getBusinessName(), seller.getStatus())));
        log.info("판매자 승인: applicationId={} sellerId={} adminId={}", applicationId, seller.getId(), adminId);
        return SellerApplicationSummary.from(application);
    }

    @Transactional
    public SellerApplicationSummary reject(Long applicationId, Long adminId, SellerApplicationRejectRequest request) {
        var application = sellerApplicationRepository.getById(applicationId);
        var now = LocalDateTime.now();
        application.reject(adminId, request.reason(), now);
        eventPublisher.publishEvent(new SellerApplicationRejectedEvent(UUID.randomUUID(), now,
            new SellerApplicationRejectedEvent.Data(applicationId, application.getApplicantId(),
                application.getRejectReason(), application.getDecidedAt())));
        log.info("판매자 신청 거절: applicationId={} adminId={}", applicationId, adminId);
        return SellerApplicationSummary.from(application);
    }

    @Transactional
    public void changeApplicantNickname(Long applicantId, String nickname, LocalDateTime changedAt) {
        sellerApplicationRepository.findAllByApplicantId(applicantId)
            .forEach(application -> application.changeApplicantNickname(nickname, changedAt));
    }

    @Transactional
    public void clearApplicantPersonalInfo(Long applicantId, LocalDateTime withdrawnAt) {
        var applications = sellerApplicationRepository.findAllByApplicantId(applicantId);
        applications.forEach(application -> application.clearPersonalInfo(withdrawnAt));
        log.info("탈퇴 회원 신청 개인정보 비움: applicantId={} count={}", applicantId, applications.size());
    }
}
