package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.DeletedChildAccounts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.ChildFavoritesRemover;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteTimes;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀 계정 삭제 때 찜 정리: 계정을 지운 자녀 계정으로 먼저 남기고(그 뒤 찜 요청 403), 그 계정의 찜을 한 문장으로 지운 뒤
 * 상품별 찜 수를 줄인다. 활동 기록 · 이벤트의 까닭은 withdrawn(MEMBER_WITHDRAWN) + 소유자 종류 CHILD다.
 */
@Service
@RequiredArgsConstructor
public class ChildFavoritesRemovalService implements ChildFavoritesRemover {

    private final DeletedChildAccounts deletedChildAccounts;
    private final MemberFavoriteRows memberFavoriteRows;
    private final FavoriteBulkRemoval bulkRemoval;

    @Override
    @Transactional
    public int removeAll(Long accountId, Long guardianId, LocalDateTime deletedAt) {
        deletedChildAccounts.markDeleted(accountId, guardianId, FavoriteTimes.toStoredPrecision(deletedAt),
            FavoriteTimes.now());
        return bulkRemoval.settle(memberFavoriteRows.deleteAllByChild(accountId));
    }
}
