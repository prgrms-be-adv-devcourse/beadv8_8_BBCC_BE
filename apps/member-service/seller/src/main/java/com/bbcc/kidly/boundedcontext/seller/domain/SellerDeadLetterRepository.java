package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerDeadLetterRepository {

    SellerDeadLetter save(SellerDeadLetter deadLetter);

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    Page<SellerDeadLetter> findByStatusOrderByIdDesc(DeadLetterStatus status, Pageable pageable);

    long countByStatus(DeadLetterStatus status);

    Optional<SellerDeadLetter> findById(Long id);

    Optional<SellerDeadLetter> findByIdForUpdate(Long id);

    default SellerDeadLetter getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(SellerErrorCode.DEAD_LETTER_NOT_FOUND));
    }
}
