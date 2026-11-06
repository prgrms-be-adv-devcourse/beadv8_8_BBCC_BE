package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChildProfileDeadLetterRepository {

    ChildProfileDeadLetter save(ChildProfileDeadLetter deadLetter);

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    Page<ChildProfileDeadLetter> findByStatusOrderByIdDesc(DeadLetterStatus status, Pageable pageable);

    long countByStatus(DeadLetterStatus status);

    Optional<ChildProfileDeadLetter> findById(Long id);

    Optional<ChildProfileDeadLetter> findByIdForUpdate(Long id);

    default ChildProfileDeadLetter getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ChildProfileErrorCode.DEAD_LETTER_NOT_FOUND));
    }
}
