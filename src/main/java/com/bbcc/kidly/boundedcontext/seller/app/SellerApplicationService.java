package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationRequest;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerApplicationResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplication;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 신청 · 조회.
 */
@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerRepository sellerRepository;

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
}
