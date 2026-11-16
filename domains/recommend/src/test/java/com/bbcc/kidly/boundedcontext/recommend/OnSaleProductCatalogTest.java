package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.OnSaleProductCatalog;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.OnSaleProductLoader;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaProductSnapshot;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnSaleProductCatalogTest {

    // DB를 다시 확인하기까지 두는 간격이다
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(5);
    // 늦게 커밋된 변경을 놓치지 않으려고 지난번에 본 수정 시각보다 이만큼 앞에서부터 다시 읽는다
    private static final Duration OVERLAP = Duration.ofSeconds(10);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 11, 16, 12, 0, 0);
    private static final Long OTHER_CATEGORY_ID = 53L;

    @Mock
    private OnSaleProductLoader loader;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T03:00:00Z"));

    private OnSaleProductCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new OnSaleProductCatalog(loader, REFRESH_INTERVAL, OVERLAP, clock);
    }

    @Test
    @DisplayName("처음 읽을 때 판매중인 상품을 모두 읽어 돌려준다")
    void loadAllOnFirstRead() {
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT), onSale(2L, UPDATED_AT)));

        assertThat(catalog.onSale(null))
            .extracting(ReplicaProductSnapshot::productId)
            .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("카테고리를 주면 그 카테고리의 상품만 돌려준다")
    void filterByCategory() {
        given(loader.loadAllOnSale()).willReturn(List.of(
            onSale(1L, UPDATED_AT),
            snapshot(2L, OTHER_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE, UPDATED_AT)));

        assertThat(catalog.onSale(OTHER_CATEGORY_ID))
            .extracting(ReplicaProductSnapshot::productId)
            .containsExactly(2L);
    }

    @Test
    @DisplayName("마지막 확인 뒤 확인 간격이 지나지 않았으면 DB를 다시 읽지 않는다")
    void doNotReadWithinInterval() {
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT)));
        catalog.onSale(null);

        clock.advance(REFRESH_INTERVAL.minusMillis(1));
        catalog.onSale(null);

        verify(loader, times(1)).loadAllOnSale();
        verify(loader, never()).loadChangedAfter(any());
        verify(loader, never()).countOnSale();
    }

    @Test
    @DisplayName("확인 간격이 지나면 지금까지 본 가장 늦은 수정 시각에서 겹치는 구간만큼 앞선 시각 뒤에 고쳐진 상품만 읽는다")
    void readChangedWithOverlapAfterInterval() {
        given(loader.loadAllOnSale()).willReturn(
            List.of(onSale(1L, UPDATED_AT), onSale(2L, UPDATED_AT.plusSeconds(3))));
        given(loader.countOnSale()).willReturn(2L);
        catalog.onSale(null);

        clock.advance(REFRESH_INTERVAL);
        catalog.onSale(null);

        // 가장 늦은 수정 시각은 12:00:03이고 10초 앞은 11:59:53이다
        verify(loader).loadChangedAfter(LocalDateTime.of(2026, 11, 16, 11, 59, 53));
        verify(loader, times(1)).loadAllOnSale();
    }

    @Test
    @DisplayName("고쳐진 상품이 판매중이면 새 값으로 바꾸거나 더하고, 판매중이 아니면 뺀다")
    void applyChangedProducts() {
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT), onSale(2L, UPDATED_AT)));
        // 1번은 이름이 바뀌었고, 2번은 판매가 중지됐고, 3번은 새로 판매를 시작했다
        given(loader.loadChangedAfter(any())).willReturn(List.of(
            snapshot(1L, PRODUCT_A_CATEGORY_ID, "바뀐 이름", ProductStatus.ON_SALE, UPDATED_AT.plusSeconds(20)),
            snapshot(2L, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.STOPPED, UPDATED_AT.plusSeconds(21)),
            onSale(3L, UPDATED_AT.plusSeconds(22))));
        given(loader.countOnSale()).willReturn(2L);
        catalog.onSale(null);

        clock.advance(REFRESH_INTERVAL);
        List<ReplicaProductSnapshot> products = catalog.onSale(null);

        assertThat(products).extracting(ReplicaProductSnapshot::productId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(products).filteredOn(product -> product.productId().equals(1L))
            .extracting(ReplicaProductSnapshot::name)
            .containsExactly("바뀐 이름");
        verify(loader, times(1)).loadAllOnSale();
    }

    @Test
    @DisplayName("고쳐진 상품을 읽고 나면 다음 확인은 새로 본 가장 늦은 수정 시각을 기준으로 한다")
    void advanceWatermarkAfterReadingChanged() {
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT)));
        given(loader.loadChangedAfter(UPDATED_AT.minus(OVERLAP)))
            .willReturn(List.of(onSale(2L, UPDATED_AT.plusSeconds(22))));
        given(loader.countOnSale()).willReturn(2L);
        catalog.onSale(null);
        clock.advance(REFRESH_INTERVAL);
        catalog.onSale(null);

        clock.advance(REFRESH_INTERVAL);
        catalog.onSale(null);

        // 가장 늦은 수정 시각은 12:00:22이고 10초 앞은 12:00:12다
        verify(loader).loadChangedAfter(LocalDateTime.of(2026, 11, 16, 12, 0, 12));
    }

    @Test
    @DisplayName("DB의 판매중 상품 수가 보관한 수와 다르면 판매중인 상품을 모두 다시 읽는다")
    void reloadAllWhenCountDiffers() {
        // 처음에는 1 · 2번이 있었지만 표가 비워진 뒤 3번만 남았다. 지워진 행은 고쳐진 상품으로 읽히지 않는다
        given(loader.loadAllOnSale())
            .willReturn(List.of(onSale(1L, UPDATED_AT), onSale(2L, UPDATED_AT)))
            .willReturn(List.of(onSale(3L, UPDATED_AT.plusSeconds(30))));
        given(loader.loadChangedAfter(any())).willReturn(List.of(onSale(3L, UPDATED_AT.plusSeconds(30))));
        given(loader.countOnSale()).willReturn(1L);
        catalog.onSale(null);

        clock.advance(REFRESH_INTERVAL);

        assertThat(catalog.onSale(null))
            .extracting(ReplicaProductSnapshot::productId)
            .containsExactly(3L);
        verify(loader, times(2)).loadAllOnSale();
    }

    @Test
    @DisplayName("처음 읽었을 때 판매중인 상품이 없었으면 다음 확인에서도 모두 읽는다")
    void loadAllAgainWhenNothingWasLoaded() {
        given(loader.loadAllOnSale())
            .willReturn(List.of())
            .willReturn(List.of(onSale(1L, UPDATED_AT)));
        assertThat(catalog.onSale(null)).isEmpty();

        clock.advance(REFRESH_INTERVAL);

        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId).containsExactly(1L);
        verify(loader, never()).loadChangedAfter(any());
    }

    @Test
    @DisplayName("앱이 뜰 때 미리 채워 두면 첫 요청은 DB를 읽지 않는다")
    void warmUpLoadsBeforeFirstRequest() {
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT)));

        catalog.warmUp();

        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId).containsExactly(1L);
        verify(loader, times(1)).loadAllOnSale();
    }

    @Test
    @DisplayName("앱이 뜰 때 미리 채우다 실패해도 예외를 올리지 않고, 첫 요청이 다시 읽는다")
    void warmUpFailureIsRetriedByFirstRequest() {
        given(loader.loadAllOnSale())
            .willThrow(new IllegalStateException("DB에 연결할 수 없습니다"))
            .willReturn(List.of(onSale(1L, UPDATED_AT)));

        catalog.warmUp();

        assertThat(catalog.onSale(null)).extracting(ReplicaProductSnapshot::productId).containsExactly(1L);
    }

    @Test
    @DisplayName("한 요청이 DB를 확인하는 동안 온 다른 요청은 기다리지 않고 지금 보관한 값을 받는다")
    void doNotWaitWhileAnotherRequestRefreshes() throws Exception {
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        given(loader.loadAllOnSale()).willReturn(List.of(onSale(1L, UPDATED_AT)));
        given(loader.loadChangedAfter(any())).willAnswer(invocation -> {
            refreshStarted.countDown();
            releaseRefresh.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        given(loader.countOnSale()).willReturn(1L);
        catalog.onSale(null);
        clock.advance(REFRESH_INTERVAL);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<ReplicaProductSnapshot>> refreshing = executor.submit(() -> catalog.onSale(null));
            assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // 첫 요청이 아직 DB를 확인하는 중이다. 두 번째 요청은 그 확인이 끝나기 전에 돌아와야 한다
            Future<List<ReplicaProductSnapshot>> waitingFree = executor.submit(() -> catalog.onSale(null));

            assertThat(waitingFree.get(1, TimeUnit.SECONDS))
                .extracting(ReplicaProductSnapshot::productId)
                .containsExactly(1L);
            releaseRefresh.countDown();
            refreshing.get(5, TimeUnit.SECONDS);
            verify(loader, times(1)).loadChangedAfter(any());
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    private static ReplicaProductSnapshot onSale(Long productId, LocalDateTime updatedAt) {
        return snapshot(productId, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE, updatedAt);
    }

    private static ReplicaProductSnapshot snapshot(Long productId, Long categoryId, String name,
        ProductStatus status, LocalDateTime updatedAt) {
        return new ReplicaProductSnapshot(productId, PRODUCT_A_SELLER_ID, categoryId, name,
            PRODUCT_A_THUMBNAIL_URL, 25_000L, status, PRODUCT_A_BRAND, PRODUCT_A_REGISTERED_AT, null,
            productA(), Set.of("110", "120"), List.of("의류", "상의"), false, updatedAt);
    }

    // 테스트가 시각을 앞으로 옮길 수 있는 시계다
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
