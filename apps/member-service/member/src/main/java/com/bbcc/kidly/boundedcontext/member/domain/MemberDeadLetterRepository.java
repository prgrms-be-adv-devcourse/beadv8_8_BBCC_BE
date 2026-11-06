package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberDeadLetterRepository {

    MemberDeadLetter save(MemberDeadLetter deadLetter);

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    Page<MemberDeadLetter> findByStatusOrderByIdDesc(DeadLetterStatus status, Pageable pageable);

    long countByStatus(DeadLetterStatus status);

    Optional<MemberDeadLetter> findById(Long id);

    Optional<MemberDeadLetter> findByIdForUpdate(Long id);

    default MemberDeadLetter getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(MemberErrorCode.DEAD_LETTER_NOT_FOUND));
    }
}
