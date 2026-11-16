package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.MemberFavoritesRemover;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때 찜 정리: 그 회원이 보호자인 찜(본인 찜 + 자녀 계정 찜)을 한 문장으로 지우며 지운 행을 받고, 상품별로 지운 수만큼 찜 수를 줄인다.
 * 지운 행 기준으로 세므로 찜이 많아도 바인드 변수 수와 상관없고, 겹친 찜 해제 · 추가와도 찜 수가 어긋나지 않는다.
 * 활동 기록 · 이벤트의 까닭은 withdrawn(MEMBER_WITHDRAWN)이고 소유자 종류로 본인 · 자녀 찜을 구분한다.
 */
@Service
@RequiredArgsConstructor
public class MemberFavoritesRemovalService implements MemberFavoritesRemover {

    private final MemberFavoriteRows memberFavoriteRows;
    private final FavoriteBulkRemoval bulkRemoval;

    @Override
    @Transactional
    public int removeAll(Long memberId) {
        return bulkRemoval.settle(memberFavoriteRows.deleteAllByGuardian(memberId));
    }
}
