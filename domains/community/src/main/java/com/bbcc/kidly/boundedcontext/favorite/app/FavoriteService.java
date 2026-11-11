package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts.ProductView;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites;
import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteRuleException;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜하기 · 찜 해제와 찜 수 증감. 둘 다 멱등이다(이미 찜 · 없는 찜도 정상으로 끝난다).
 * <ul>
 *   <li>찜: 이미 찜했으면 상품 상태와 상관없이 끝 → 상품 정보 없음 404 → 판매 종료 409 → 따로 연 트랜잭션에서 저장 + 찜 수 +1.
 *       확인 뒤 같은 찜이 먼저 저장되면(동시 찜) (회원, 상품) 유일 제약에 막히는데, 그 위반이면 다시 조회하지 않고 이미 찜으로 끝낸다
 *       (그 사이 해제됐어도 해제가 뒤에 일어난 것과 같은 결과). 다른 위반은 던진다</li>
 *   <li>해제: 한 트랜잭션에서 행을 지우고, 지운 행이 있을 때만 찜 수 −1. 찜 수 행이 없으면 실패한다(어긋남을 넘기지 않음)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    // (회원, 상품)당 찜 1개를 지키는 유일 제약
    static final String MEMBER_PRODUCT_UNIQUE = "uk_favorite_member_product";

    private final Favorites favorites;
    private final FavoriteStatsCounter favoriteStatsCounter;
    private final FavoriteProducts favoriteProducts;
    private final FavoriteSeparateTransaction separateTransaction;
    private final FavoriteActivityLog activityLog;

    public void add(Long memberId, String productId) {
        Long id = FavoriteIds.productId(productId);
        if (favorites.existsByMemberIdAndProductId(memberId, id)) {
            return;
        }
        ProductView product = favoriteProducts.find(List.of(id)).get(id);
        if (product == null) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_NOT_FOUND);
        }
        Favorite favorite = create(memberId, id, product.saleEnded());
        try {
            separateTransaction.run(() -> {
                Favorite saved = favorites.saveAndFlush(favorite);
                favoriteStatsCounter.increase(id, FavoriteTimes.now());
                // 새로 생긴 찜만 남는다(동시 찜으로 막힌 저장은 이 트랜잭션과 함께 되돌려진다)
                activityLog.favoriteAdded(saved.getId(), id);
            });
        } catch (DataIntegrityViolationException e) {
            // 같은 찜이 먼저 저장됐다. 다시 보지 않고 이미 찜으로 끝낸다(그 사이 해제됐어도 해제가 뒤에 일어난 것과 같은 결과)
            if (!FavoriteUniqueViolations.isViolationOf(e, MEMBER_PRODUCT_UNIQUE)) {
                throw e;
            }
        }
    }

    @Transactional
    public void remove(Long memberId, String productId) {
        Long id = FavoriteIds.productId(productId);
        List<Long> deleted = favorites.deleteByMember(memberId, id);
        if (deleted.isEmpty()) {
            return;
        }
        if (favoriteStatsCounter.decreaseBy(id, 1, FavoriteTimes.now()) == 0) {
            throw new IllegalStateException("찜 수 행이 없습니다: productId=" + id);
        }
        activityLog.favoriteRemoved(deleted.getFirst(), id);
    }

    private static Favorite create(Long memberId, Long productId, boolean saleEnded) {
        try {
            return Favorite.create(memberId, productId, saleEnded);
        } catch (FavoriteRuleException e) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_SALE_ENDED);
        }
    }
}
