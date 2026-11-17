package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import java.util.List;
import java.util.Optional;

public interface SellerApplicationRepository {

    SellerApplication save(SellerApplication application);

    Optional<SellerApplication> findById(Long id);

    Optional<SellerApplication> findFirstByApplicantIdOrderByIdDesc(Long applicantId);

    List<SellerApplication> findAllByApplicantId(Long applicantId);

    boolean existsByApplicantIdAndStatus(Long applicantId, SellerApplicationStatus status);

    long count();

    default SellerApplication getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
