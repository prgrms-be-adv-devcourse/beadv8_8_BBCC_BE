package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.app.port.DeletedChildAccounts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts.ProductView;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites;
import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteRuleException;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜하기 · 찜 해제와 찜 수 증감. 소유자(회원 본인 · 자녀 계정)마다 따로 다룬다. 둘 다 멱등이다(이미 찜 · 없는 찜도 정상으로 끝난다).
 * <ul>
 *   <li>찜: 이미 찜했으면 상품 상태와 상관없이 끝 → 상품 정보 없음 404 → 판매 종료 409 → 따로 연 트랜잭션에서 저장 + 찜 수 +1.
 *       확인 뒤 같은 찜이 먼저 저장되면(동시 찜) (소유자, 상품) 유일 제약에 막히는데, 그 위반이면 다시 조회하지 않고 이미 찜으로 끝낸다
 *       (그 사이 해제됐어도 해제가 뒤에 일어난 것과 같은 결과). 다른 위반은 던진다</li>
 *   <li>자녀 계정 찜은 저장 트랜잭션 안에서 그 계정의 공유 잠금을 잡고 지운 계정인지 다시 본다(지웠으면 403). 삭제 처리는 같은 키의
 *       배타 잠금을 잡으므로 겹친 추가는 삭제 전에 끝나 지워지거나, 삭제 뒤에 403이 된다</li>
 *   <li>해제: 한 트랜잭션에서 행을 지우고, 지운 행이 있을 때만 찜 수 −1. 찜 수 행이 없으면 실패한다(어긋남을 넘기지 않음)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    // (소유자 종류, 소유자 ID, 상품)당 찜 1개를 지키는 유일 제약
    static final String OWNER_PRODUCT_UNIQUE = "uk_favorite_owner_product";

    private final Favorites favorites;
    private final FavoriteStatsCounter favoriteStatsCounter;
    private final FavoriteProducts favoriteProducts;
    private final FavoriteSeparateTransaction separateTransaction;
    private final FavoriteActivityLog activityLog;
    private final DeletedChildAccounts deletedChildAccounts;

    public void add(FavoriteActor actor, String productId) {
        add(actor.toOwner(), productId);
    }

    public void add(FavoriteOwner owner, String productId) {
        Long id = FavoriteIds.productId(productId);
        if (favorites.existsByOwnerTypeAndOwnerIdAndProductId(owner.type(), owner.id(), id)) {
            return;
        }
        ProductView product = favoriteProducts.find(List.of(id)).get(id);
        if (product == null) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_NOT_FOUND);
        }
        Favorite favorite = create(owner, id, product.saleEnded());
        try {
            separateTransaction.run(() -> {
                ensureChildAccountActive(owner);
                Favorite saved = favorites.saveAndFlush(favorite);
                favoriteStatsCounter.increase(id, FavoriteTimes.now());
                // 새로 생긴 찜만 남는다(동시 찜으로 막힌 저장은 이 트랜잭션과 함께 되돌려진다)
                activityLog.favoriteAdded(saved.getId(), owner, id);
            });
        } catch (DataIntegrityViolationException e) {
            // 같은 찜이 먼저 저장됐다. 다시 보지 않고 이미 찜으로 끝낸다(그 사이 해제됐어도 해제가 뒤에 일어난 것과 같은 결과)
            if (!FavoriteUniqueViolations.isViolationOf(e, OWNER_PRODUCT_UNIQUE)) {
                throw e;
            }
        }
    }

    @Transactional
    public void remove(FavoriteActor actor, String productId) {
        remove(actor.toOwner(), productId);
    }

    @Transactional
    public void remove(FavoriteOwner owner, String productId) {
        Long id = FavoriteIds.productId(productId);
        List<Long> deleted = favorites.deleteByOwner(owner.type().name(), owner.id(), id);
        if (deleted.isEmpty()) {
            return;
        }
        if (favoriteStatsCounter.decreaseBy(id, 1, FavoriteTimes.now()) == 0) {
            throw new IllegalStateException("찜 수 행이 없습니다: productId=" + id);
        }
        activityLog.favoriteRemoved(deleted.getFirst(), owner, id);
    }

    // 자녀 계정이면 그 계정의 공유 잠금을 잡고(겹친 삭제 처리가 끝나기를 기다림) 지운 계정인지 다시 본다. 회원은 잠그지 않는다
    private void ensureChildAccountActive(FavoriteOwner owner) {
        if (!owner.isChild()) {
            return;
        }
        deletedChildAccounts.lockChildAccountShared(owner.id());
        if (deletedChildAccounts.existsById(owner.id())) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        }
    }

    private static Favorite create(FavoriteOwner owner, Long productId, boolean saleEnded) {
        try {
            return Favorite.create(owner, productId, saleEnded);
        } catch (FavoriteRuleException e) {
            throw new BusinessException(FavoriteErrorCode.FAVORITE_PRODUCT_SALE_ENDED);
        }
    }
}
