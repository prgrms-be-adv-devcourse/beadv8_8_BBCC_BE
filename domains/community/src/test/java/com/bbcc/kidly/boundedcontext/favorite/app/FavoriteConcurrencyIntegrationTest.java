package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 찜의 동시성: 같은 회원 · 상품 동시 찜은 1행 · 찜 수 1, 동시 찜 · 해제가 섞여도 찜 수 = 실제 행 수, 여러 회원 동시 찜도 같다.
 * 모든 결과는 성공이어야 한다(찜 · 해제는 멱등이라 업무 거절도 없다).
 */
@IntegrationTest
class FavoriteConcurrencyIntegrationTest {

    private static final long PRODUCT = 14L;
    private static final int ROUNDS = 5;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void replica() {
        jdbcTemplate.update("insert into community.replica_product (product_id, name, thumbnail_url, display_status, "
            + "source_occurred_at, created_at, updated_at) values (?, '상품', null, 'ON_SALE', now(), now(), now())",
            PRODUCT);
    }

    @Test
    @DisplayName("같은 회원이 같은 상품을 동시에 8번 찜해도 행 1개 · 찜 수 1이고 모두 성공한다")
    void sameMemberConcurrentAdds() throws Exception {
        List<Object> results = runConcurrently(8, i -> () -> {
            favoriteService.add(1L, "14");
            return "ok";
        });

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(rows()).isEqualTo(1);
        assertCountMatchesRows();
    }

    @Test
    @DisplayName("같은 회원의 동시 찜 · 해제를 여러 라운드 섞어도 찜 수 = 실제 행 수(0 또는 1)")
    void concurrentAddAndRemove() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            List<Object> results = runConcurrently(6, i -> () -> {
                if (i % 2 == 0) {
                    favoriteService.add(1L, "14");
                } else {
                    favoriteService.remove(1L, "14");
                }
                return "ok";
            });

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(rows()).isBetween(0, 1);
            assertCountMatchesRows();
        }
    }

    @Test
    @DisplayName("여러 회원이 같은 상품을 동시에 찜하면 찜 수 = 회원 수 = 행 수")
    void manyMembersConcurrentAdds() throws Exception {
        List<Object> results = runConcurrently(10, i -> () -> {
            favoriteService.add(100L + i, "14");
            return "ok";
        });

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(rows()).isEqualTo(10);
        assertCountMatchesRows();
    }

    private void assertCountMatchesRows() {
        Long count = jdbcTemplate.queryForList("select favorite_count from community.favorite_stats "
            + "where product_id = ?", Long.class, PRODUCT).stream().findFirst().orElse(0L);
        assertThat(count).isEqualTo((long) rows());
    }

    private int rows() {
        return jdbcTemplate.queryForObject("select count(*) from community.favorite where product_id = ?",
            Integer.class, PRODUCT);
    }

    private static List<Object> runConcurrently(int count, IntFunction<Callable<Object>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<Object> work = task.apply(i);
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return work.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
