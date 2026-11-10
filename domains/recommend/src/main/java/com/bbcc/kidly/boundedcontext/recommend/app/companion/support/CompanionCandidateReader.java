package com.bbcc.kidly.boundedcontext.recommend.app.companion.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 함께 사면 좋은 상품의 후보를 트랜잭션 안에서 읽는다.
 */
@Component
public class CompanionCandidateReader {

    @Transactional(readOnly = true)
    public CompanionCandidates read(Long memberId, Long productId, Long childId) {
        throw new UnsupportedOperationException("다음 테스트에서 구현한다");
    }
}
