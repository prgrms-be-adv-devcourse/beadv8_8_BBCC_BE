package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.Favorites;
import com.bbcc.kidly.boundedcontext.favorite.domain.Favorite;
import com.bbcc.kidly.global.error.BusinessException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

class FavoriteServiceTest {

    private static final long ME = 1L;
    private static final long PRODUCT = 14L;

    private final FakeFavorites favorites = new FakeFavorites();
    private final FakeStats stats = new FakeStats();
    private final FakeProducts products = new FakeProducts();
    private final FavoriteService service =
        new FavoriteService(favorites, stats, products, new FavoriteSeparateTransaction());

    @Test
    @DisplayName("판매 중 상품을 찜하면 저장하고 찜 수 +1")
    void add() {
        products.put(PRODUCT, false);

        service.add(ME, String.valueOf(PRODUCT));

        assertThat(favorites.rows).containsKey(PRODUCT);
        assertThat(stats.changes).containsExactly("+" + PRODUCT);
    }

    @Test
    @DisplayName("이미 찜했으면 상품이 판매 종료됐어도 그대로 끝낸다(저장 · 찜 수 변화 없음)")
    void alreadyFavoritedIsIdempotent() {
        products.put(PRODUCT, false);
        service.add(ME, String.valueOf(PRODUCT));
        products.put(PRODUCT, true);

        service.add(ME, String.valueOf(PRODUCT));

        assertThat(favorites.saves).isEqualTo(1);
        assertThat(stats.changes).containsExactly("+" + PRODUCT);
    }

    @Test
    @DisplayName("상품 복사본에 없으면 FAVORITE_PRODUCT_NOT_FOUND, 판매 종료면 FAVORITE_PRODUCT_SALE_ENDED")
    void notFoundAndSaleEnded() {
        assertError(() -> service.add(ME, "999"), FavoriteErrorCode.FAVORITE_PRODUCT_NOT_FOUND);
        products.put(PRODUCT, true);
        assertError(() -> service.add(ME, String.valueOf(PRODUCT)), FavoriteErrorCode.FAVORITE_PRODUCT_SALE_ENDED);
        assertThat(favorites.saves).isZero();
        assertThat(stats.changes).isEmpty();
    }

    @Test
    @DisplayName("확인 뒤 저장이 (회원, 상품) 유일 제약에 막히면(동시 찜) 이미 찜으로 끝내고 다른 위반은 던진다")
    void concurrentDuplicate() {
        products.put(PRODUCT, false);
        favorites.violation = violation("23505", FavoriteService.MEMBER_PRODUCT_UNIQUE);
        favorites.insertedByOther = true;

        service.add(ME, String.valueOf(PRODUCT));
        assertThat(stats.changes).isEmpty();

        favorites.rows.clear();
        favorites.violation = violation("23502", null);
        assertThatThrownBy(() -> service.add(ME, String.valueOf(PRODUCT)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장이 (회원, 상품) 유일 제약에 막힌 뒤 다시 보면 행이 없어도(그 사이 해제) 예외 없이 이미 찜으로 끝낸다")
    void duplicateThenRemovedByOther() {
        products.put(PRODUCT, false);
        favorites.violation = violation("23505", FavoriteService.MEMBER_PRODUCT_UNIQUE);
        favorites.insertedByOther = false;

        service.add(ME, String.valueOf(PRODUCT));

        assertThat(favorites.rows).isEmpty();
        assertThat(stats.changes).isEmpty();
    }

    @Test
    @DisplayName("해제: 찜이 있으면 지우고 찜 수 −1, 없으면 그대로(찜 수 변화 없음)")
    void remove() {
        products.put(PRODUCT, false);
        service.add(ME, String.valueOf(PRODUCT));
        stats.changes.clear();

        service.remove(ME, String.valueOf(PRODUCT));
        service.remove(ME, String.valueOf(PRODUCT));

        assertThat(favorites.rows).isEmpty();
        assertThat(stats.changes).containsExactly("-" + PRODUCT);
    }

    @Test
    @DisplayName("해제할 때 찜 수 행이 없으면(바뀐 행 0) 조용히 넘기지 않고 예외로 실패한다")
    void missingStatsRowFails() {
        products.put(PRODUCT, false);
        service.add(ME, String.valueOf(PRODUCT));
        stats.decreasedRows = 0;

        assertThatThrownBy(() -> service.remove(ME, String.valueOf(PRODUCT)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("productId=" + PRODUCT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "0", "-1", "1.5"})
    @NullSource
    @DisplayName("상품 ID가 양의 정수가 아니면 INVALID_PRODUCT_ID(찜 · 해제)")
    void invalidProductId(String productId) {
        assertError(() -> service.add(ME, productId), FavoriteErrorCode.INVALID_PRODUCT_ID);
        assertError(() -> service.remove(ME, productId), FavoriteErrorCode.INVALID_PRODUCT_ID);
    }

    private static void assertError(Runnable call, FavoriteErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static DataIntegrityViolationException violation(String sqlState, String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
            new ConstraintViolationException("could not execute statement", new SQLException("violation", sqlState),
                constraint));
    }

    private static final class FakeProducts implements FavoriteProducts {

        private final Map<Long, ProductView> rows = new HashMap<>();

        void put(long productId, boolean saleEnded) {
            rows.put(productId, new ProductView(productId, "상품", null, saleEnded ? "SALE_ENDED" : "ON_SALE",
                saleEnded));
        }

        @Override
        public Map<Long, ProductView> find(Collection<Long> productIds) {
            Map<Long, ProductView> found = new HashMap<>();
            productIds.stream().filter(rows::containsKey).forEach(id -> found.put(id, rows.get(id)));
            return found;
        }
    }

    private static final class FakeStats implements FavoriteStatsCounter {

        private final List<String> changes = new ArrayList<>();
        private int decreasedRows = 1;

        @Override
        public void increase(Long productId, LocalDateTime now) {
            changes.add("+" + productId);
        }

        @Override
        public int decreaseBy(Long productId, int count, LocalDateTime now) {
            changes.add("-" + productId);
            return decreasedRows;
        }
    }

    private static final class FakeFavorites implements Favorites {

        private final Map<Long, Favorite> rows = new HashMap<>();
        private int saves;
        private DataIntegrityViolationException violation;
        private boolean insertedByOther;

        @Override
        public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
            return rows.containsKey(productId);
        }

        @Override
        public Favorite saveAndFlush(Favorite favorite) {
            if (violation != null) {
                if (insertedByOther) {
                    rows.put(favorite.getProductId(), favorite);
                }
                throw violation;
            }
            saves++;
            rows.put(favorite.getProductId(), favorite);
            return favorite;
        }

        @Override
        public int deleteByMember(Long memberId, Long productId) {
            return rows.remove(productId) == null ? 0 : 1;
        }

        @Override
        public List<FavoriteRow> findMine(Long memberId, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FavoriteRow> findMineAfter(Long memberId, LocalDateTime createdAt, Long id, Limit limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FavoriteRow> findMineIn(Long memberId, Collection<Long> productIds) {
            throw new UnsupportedOperationException();
        }
    }
}
