package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaProductSnapshot;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 판매중인 상품의 스냅숏을 메모리에 한 벌만 보관한다.
 * 요청마다 판매중인 상품 복사본을 모두 읽으면 요청 수만큼 힙에 엔티티가 올라가 전체 GC가 계속 돌기 때문이다.
 * 보관한 값은 확인 간격마다 한 요청이 DB와 맞추므로, 상품이 바뀐 뒤 목록에 반영되기까지 최대 확인 간격만큼 늦다.
 * 보관은 Pod마다 따로 하므로 Pod끼리 반영 시점이 조금 다를 수 있다.
 * 맞출 때는 지난번에 본 수정 시각보다 겹치는 구간만큼 앞에서부터 고쳐진 상품만 읽고, 판매중인 상품 수가 다르면 모두 다시 읽는다.
 */
@Slf4j
@Component
public class OnSaleProductCatalog {

    private final OnSaleProductLoader loader;
    private final Duration refreshInterval;
    private final Duration overlap;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile Map<Long, ReplicaProductSnapshot> products = new ConcurrentHashMap<>();
    // 한 번도 채우지 않았으면 null이다
    private volatile Instant lastCheckedAt;
    // 지금까지 읽은 스냅숏 중 가장 늦은 수정 시각이다. 읽은 것이 없으면 null이다
    private volatile LocalDateTime latestUpdatedAt;

    @Autowired
    public OnSaleProductCatalog(
        OnSaleProductLoader loader,
        @Value("${kidly.recommend.on-sale-products.refresh-interval:PT5S}") Duration refreshInterval,
        @Value("${kidly.recommend.on-sale-products.overlap:PT10S}") Duration overlap) {
        this(loader, refreshInterval, overlap, Clock.systemUTC());
    }

    public OnSaleProductCatalog(OnSaleProductLoader loader, Duration refreshInterval, Duration overlap,
        Clock clock) {
        this.loader = loader;
        this.refreshInterval = refreshInterval;
        this.overlap = overlap;
        this.clock = clock;
    }

    /**
     * 판매중인 상품의 스냅숏을 돌려준다. categoryId가 null이 아니면 카테고리가 같은 상품만 돌려준다.
     */
    public List<ReplicaProductSnapshot> onSale(Long categoryId) {
        refreshIfNeeded();
        List<ReplicaProductSnapshot> result = new ArrayList<>();
        for (ReplicaProductSnapshot snapshot : products.values()) {
            if (categoryId == null || categoryId.equals(snapshot.categoryId())) {
                result.add(snapshot);
            }
        }
        return result;
    }

    /**
     * 앱이 뜬 뒤 보관한 값을 미리 채워 첫 요청이 전체 읽기 시간을 떠안지 않게 한다.
     * 채우다 실패해도 앱이 뜨는 것을 막지 않는다. 채우지 못한 채로 남으므로 첫 요청이 다시 읽는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            fillIfNeverFilled();
        } catch (RuntimeException e) {
            log.warn("판매중인 상품을 미리 채우지 못했습니다. 첫 요청이 다시 읽습니다.", e);
        }
    }

    private void refreshIfNeeded() {
        if (lastCheckedAt == null) {
            fillIfNeverFilled();
            return;
        }
        if (!isIntervalElapsed()) {
            return;
        }
        // 다른 요청이 이미 확인하는 중이면 기다리지 않고 지금 보관한 값을 쓴다
        if (!refreshLock.tryLock()) {
            return;
        }
        try {
            if (isIntervalElapsed()) {
                refresh();
            }
        } finally {
            refreshLock.unlock();
        }
    }

    // 돌려줄 값이 아직 없으므로 다른 요청이 채우는 중이면 끝날 때까지 기다린다
    private void fillIfNeverFilled() {
        refreshLock.lock();
        try {
            if (lastCheckedAt == null) {
                refresh();
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean isIntervalElapsed() {
        return Duration.between(lastCheckedAt, clock.instant()).compareTo(refreshInterval) >= 0;
    }

    // 로더가 예외를 던지면 마지막 확인 시각을 바꾸지 않으므로 다음 요청이 다시 확인한다
    private void refresh() {
        if (latestUpdatedAt == null) {
            reloadAll();
        } else {
            applyChanged();
        }
        lastCheckedAt = clock.instant();
    }

    private void applyChanged() {
        List<ReplicaProductSnapshot> changed = loader.loadChangedAfter(latestUpdatedAt.minus(overlap));
        for (ReplicaProductSnapshot snapshot : changed) {
            raiseLatestUpdatedAt(snapshot);
            if (snapshot.status() == ProductStatus.ON_SALE) {
                products.put(snapshot.productId(), snapshot);
            } else {
                products.remove(snapshot.productId());
            }
        }
        // 지워진 행은 고쳐진 상품으로 읽히지 않으므로, 판매중인 상품 수가 다르면 모두 다시 읽는다
        if (loader.countOnSale() != products.size()) {
            reloadAll();
        }
    }

    // 새 Map을 다 만든 뒤 바꿔 끼워서, 읽는 요청이 반쯤 찬 Map을 보지 않게 한다
    private void reloadAll() {
        Map<Long, ReplicaProductSnapshot> loaded = new ConcurrentHashMap<>();
        for (ReplicaProductSnapshot snapshot : loader.loadAllOnSale()) {
            raiseLatestUpdatedAt(snapshot);
            loaded.put(snapshot.productId(), snapshot);
        }
        products = loaded;
    }

    private void raiseLatestUpdatedAt(ReplicaProductSnapshot snapshot) {
        LocalDateTime updatedAt = snapshot.updatedAt();
        if (updatedAt == null) {
            return;
        }
        if (latestUpdatedAt == null || updatedAt.isAfter(latestUpdatedAt)) {
            latestUpdatedAt = updatedAt;
        }
    }
}
