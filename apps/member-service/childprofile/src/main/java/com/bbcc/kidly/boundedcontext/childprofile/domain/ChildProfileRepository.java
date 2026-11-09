package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository {

    ChildProfile save(ChildProfile child);

    Optional<ChildProfile> findByIdAndMemberId(Long id, Long memberId);

    List<ChildProfile> findAllByMemberId(Long memberId);

    // 삭제되지 않은 자녀 전체
    List<ChildProfile> findAll();

    long count();

    default ChildProfile getByIdAndMemberId(Long id, Long memberId) {
        return findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
