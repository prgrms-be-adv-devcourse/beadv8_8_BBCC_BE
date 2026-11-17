package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog.RemovedFavorite;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows.DeletedFavorite;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwnerType;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteTimes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 한 문장으로 지운 찜들의 뒷정리: 상품별로 지운 수만큼 찜 수를 줄이고(상품마다 한 문장, 상품 순서라 겹친 정리도 잠금 순서가 같다)
 * 지운 행을 활동 기록에 한 문장으로 남긴다. 찜 수 행이 없으면 실패한다(어긋남을 넘기지 않음). 부르는 쪽 트랜잭션에서 돈다.
 */
@Component
@RequiredArgsConstructor
class FavoriteBulkRemoval {

    private final FavoriteStatsCounter favoriteStatsCounter;
    private final FavoriteActivityLog activityLog;

    int settle(List<DeletedFavorite> deleted) {
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
        activityLog.favoritesRemovedByWithdrawal(deleted.stream()
            .map(favorite -> new RemovedFavorite(favorite.getId(), favorite.getProductId(),
                new FavoriteOwner(FavoriteOwnerType.valueOf(favorite.getOwnerType()), favorite.getOwnerId(),
                    favorite.getGuardianId())))
            .toList());
        return deleted.size();
    }
}
