package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ChildFavorites;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ChildAccountDeletionRecorder;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 계정 삭제 반영. 찜 정리는 찜 쪽이 같은 트랜잭션에 합류해 한다(커뮤니티에서 자녀 계정이 쓰는 것은 지금 찜뿐이다).
 */
@Service
@RequiredArgsConstructor
public class ChildAccountDeletionService implements ChildAccountDeletionRecorder {

    private final ChildFavorites childFavorites;

    @Override
    @Transactional
    public void record(Long accountId, Long guardianId, LocalDateTime deletedAt) {
        childFavorites.removeAll(accountId, guardianId, deletedAt);
    }
}
