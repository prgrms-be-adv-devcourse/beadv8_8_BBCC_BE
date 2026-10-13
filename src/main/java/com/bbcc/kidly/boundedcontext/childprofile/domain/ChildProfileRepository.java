package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.util.List;
import java.util.Optional;

/**
 * 조회는 항상 회원 ID와 함께 한다. 없는 자녀 · 남의 자녀 · 삭제된 자녀를 구분하지 않고 모두 없는 것으로 본다.
 */
public interface ChildProfileRepository {

    ChildProfile save(ChildProfile child);

    Optional<ChildProfile> findByIdAndMemberId(Long id, Long memberId);

    // 등록 순. 성장 이력을 함께 읽는다
    List<ChildProfile> findAllByMemberId(Long memberId);

    default ChildProfile getByIdAndMemberId(Long id, Long memberId) {
        return findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
