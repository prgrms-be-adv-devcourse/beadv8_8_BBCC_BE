package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.Optional;

public interface SellerApplicationRepository {

    SellerApplication save(SellerApplication application);

    Optional<SellerApplication> findById(Long id);

    // 회원의 가장 최근 신청
    Optional<SellerApplication> findFirstByApplicantIdOrderByIdDesc(Long applicantId);

    boolean existsByApplicantIdAndStatus(Long applicantId, SellerApplicationStatus status);

    default SellerApplication getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
