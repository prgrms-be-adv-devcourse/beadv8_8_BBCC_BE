package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuthDeadLetterRepository {

    AuthDeadLetter save(AuthDeadLetter deadLetter);

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    Page<AuthDeadLetter> findByStatusOrderByIdDesc(DeadLetterStatus status, Pageable pageable);

    long countByStatus(DeadLetterStatus status);

    Optional<AuthDeadLetter> findById(Long id);

    Optional<AuthDeadLetter> findByIdForUpdate(Long id);

    default AuthDeadLetter getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(AuthErrorCode.DEAD_LETTER_NOT_FOUND));
    }
}
