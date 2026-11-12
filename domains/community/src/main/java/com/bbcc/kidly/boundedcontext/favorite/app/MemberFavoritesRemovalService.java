package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog.RemovedFavorite;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows.DeletedFavorite;
import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.MemberFavoritesRemover;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteTimes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때 찜 정리: 그 회원의 찜을 한 문장으로 지우며 지운 행의 상품 ID를 받고, 상품별로 지운 수만큼 찜 수를 줄인다(상품마다 한 문장).
 * 지운 행 기준으로 세므로 찜이 많아도 바인드 변수 수와 상관없고, 겹친 찜 해제 · 추가와도 찜 수가 어긋나지 않는다.
 * 찜 수 행이 없으면 실패한다(어긋남을 넘기지 않음). 상품 순서대로 줄여 여러 탈퇴가 겹쳐도 잠금 순서가 같다.
 */
@Service
@RequiredArgsConstructor
public class MemberFavoritesRemovalService implements MemberFavoritesRemover {

    private final MemberFavoriteRows memberFavoriteRows;
    private final FavoriteStatsCounter favoriteStatsCounter;
    private final FavoriteActivityLog activityLog;

    @Override
    @Transactional
    public int removeAll(Long memberId) {
        List<DeletedFavorite> deleted = memberFavoriteRows.deleteAllByMember(memberId);
        if (deleted.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> perProduct = new TreeMap<>();
        deleted.forEach(favorite -> perProduct.merge(favorite.getProductId(), 1, Integer::sum));
        LocalDateTime now = FavoriteTimes.now();
        perProduct.forEach((productId, count) -> {
            if (favoriteStatsCounter.decreaseBy(productId, count, now) == 0) {
                throw new IllegalStateException("찜 수 행이 없습니다: productId=" + productId);
            }
        });
        // 지운 행을 그대로 한 문장으로 남긴다(까닭 withdrawn, 행마다 한 건)
        activityLog.favoritesRemovedByWithdrawal(memberId, deleted.stream()
            .map(favorite -> new RemovedFavorite(favorite.getId(), favorite.getProductId()))
            .toList());
        return deleted.size();
    }
}
