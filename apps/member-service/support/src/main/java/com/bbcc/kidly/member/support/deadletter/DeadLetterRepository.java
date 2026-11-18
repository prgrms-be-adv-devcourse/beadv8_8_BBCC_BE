package com.bbcc.kidly.member.support.deadletter;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DeadLetterRepository<T extends DeadLetter> {

    T save(T deadLetter);

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    Page<T> findByStatusOrderByIdDesc(DeadLetterStatus status, Pageable pageable);

    long countByStatus(DeadLetterStatus status);

    Optional<T> findById(Long id);

    Optional<T> findByIdForUpdate(Long id);

    default T getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(DeadLetterErrorCode.DEAD_LETTER_NOT_FOUND));
    }
}
