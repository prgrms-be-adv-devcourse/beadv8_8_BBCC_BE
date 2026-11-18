package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.favorite.app.port.DeletedChildAccounts;
import com.bbcc.kidly.boundedcontext.favorite.app.port.in.ChildFavoritesRemover;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import com.bbcc.kidly.boundedcontext.review.fixture.ReviewRows;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀 계정 삭제 처리와 그 계정의 찜 추가가 겹쳐도 지운 계정의 찜이 남지 않는다(같은 계정 키의 트랜잭션 advisory lock으로 직렬화).
 * 삭제가 먼저 끝나면 추가는 저장 트랜잭션 안에서 403이고, 추가가 먼저 끝나면 삭제가 그 찜을 지운다. 찜 수는 실제 행 수와 같다.
 */
@IntegrationTest
class ChildFavoriteDeletionConcurrencyIntegrationTest {

    private static final long GUARDIAN = 70L;
    private static final int ROUNDS = 30;
    private static final List<String> PRODUCTS = List.of("14", "15", "16", "17");
    private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private ChildFavoritesRemover childFavoritesRemover;

    @Autowired
    private DeletedChildAccounts deletedChildAccounts;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void products() {
        for (String product : PRODUCTS) {
            new ReviewRows(jdbcTemplate).replicaProduct(Long.valueOf(product)).insert();
        }
    }

    @Test
    @DisplayName("삭제 처리가 끝난 계정의 찜 추가는 저장 트랜잭션 안에서 403이고 행 · 찜 수가 생기지 않는다")
    void addAfterDeletionForbidden() {
        childFavoritesRemover.removeAll(1L, GUARDIAN, DELETED_AT);

        assertThatThrownBy(() -> favoriteService.add(FavoriteOwner.child(1L, GUARDIAN), "14"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED));
        assertThat(childRows()).isZero();
        assertThat(statsMatchRows()).isTrue();
        // 회원 본인 찜은 막지 않는다(같은 숫자 ID라도)
        favoriteService.add(FavoriteOwner.member(1L), "14");
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.favorite", Long.class)).isOne();
    }

    @Test
    @DisplayName("같은 자녀 계정의 찜 추가와 삭제 처리를 동시에 여러 번 돌려도 지운 계정의 찜은 0개이고 찜 수 = 실제 행 수")
    void concurrentAddAndDeletionLeaveNothing() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (long account = 1; account <= ROUNDS; account++) {
                FavoriteOwner child = FavoriteOwner.child(account, GUARDIAN);
                long accountId = account;
                CyclicBarrier start = new CyclicBarrier(2);
                Future<?> adds = executor.submit(() -> {
                    await(start);
                    for (String product : PRODUCTS) {
                        try {
                            favoriteService.add(child, product);
                        } catch (BusinessException e) {
                            assertThat(e.getErrorCode()).isEqualTo(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
                        }
                    }
                    return null;
                });
                Future<?> deletion = executor.submit(() -> {
                    await(start);
                    childFavoritesRemover.removeAll(accountId, GUARDIAN, DELETED_AT);
                    return null;
                });
                adds.get(20, TimeUnit.SECONDS);
                deletion.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(childRows()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from community.deleted_child_account", Long.class))
            .isEqualTo(ROUNDS);
        assertThat(statsMatchRows()).isTrue();
    }

    @Test
    @DisplayName("삭제 처리 중(배타 잠금을 쥔 동안)에는 그 계정의 찜 추가가 기다리고, 삭제가 커밋되면 403이다")
    void addWaitsForDeletion() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                deletedChildAccounts.lockChildAccountExclusive(1L);
                deletedChildAccounts.markDeleted(1L, GUARDIAN, DELETED_AT, DELETED_AT);
                locked.countDown();
                awaitLatch(release);
            }));
            awaitLatch(locked);
            Future<Object> add = executor.submit(() -> {
                try {
                    favoriteService.add(FavoriteOwner.child(1L, GUARDIAN), "14");
                    return "added";
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            });

            assertThatThrownBy(() -> add.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            deletion.get(10, TimeUnit.SECONDS);
            assertThat(add.get(10, TimeUnit.SECONDS)).isEqualTo(FavoriteErrorCode.FAVORITE_CHILD_ACCOUNT_DELETED);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(childRows()).isZero();
    }

    @Test
    @DisplayName("찜 추가 저장 중(공유 잠금을 쥔 동안)에는 그 계정의 삭제 처리가 기다리고, 추가가 커밋된 뒤 그 찜까지 지운다")
    void deletionWaitsForAdd() throws Exception {
        favoriteService.add(FavoriteOwner.child(1L, GUARDIAN), "14");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> adding = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                deletedChildAccounts.lockChildAccountShared(1L);
                locked.countDown();
                awaitLatch(release);
            }));
            awaitLatch(locked);
            Future<Integer> deletion = executor.submit(() -> childFavoritesRemover.removeAll(1L, GUARDIAN, DELETED_AT));

            assertThatThrownBy(() -> deletion.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            adding.get(10, TimeUnit.SECONDS);
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isOne();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(childRows()).isZero();
        assertThat(statsMatchRows()).isTrue();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private long childRows() {
        return jdbcTemplate.queryForObject("select count(*) from community.favorite where owner_type = 'CHILD'",
            Long.class);
    }

    // 상품마다 찜 수 = 실제 찜 행 수(찜 수 행이 없으면 0으로 본다)
    private boolean statsMatchRows() {
        List<String> mismatched = new ArrayList<>(jdbcTemplate.queryForList("select p.product_id::text "
            + "from community.replica_product p "
            + "left join community.favorite_stats s on s.product_id = p.product_id "
            + "where coalesce(s.favorite_count, 0) <> (select count(*) from community.favorite f "
            + "where f.product_id = p.product_id)", String.class));
        return mismatched.isEmpty();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
