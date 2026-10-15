package com.bbcc.kidly.boundedcontext.settlement.app.batch;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.util.Assert;

/**
 * 정산할 판매자 ID를 오름차순으로 한 페이지씩 읽는다. 위치를 저장하지 않으므로 다시 실행하면 대기 항목이 남은 판매자만 다시 읽는다.
 */
public class SettlementSellerReader implements ItemReader<Long> {

    private final SettlementSellerQuery settlementSellerQuery;
    private final LocalDateTime cutoffAt;
    private final int pageSize;
    private final Deque<Long> buffer = new ArrayDeque<>();
    private Long lastSellerId = 0L;
    private boolean lastPage;

    public SettlementSellerReader(SettlementSellerQuery settlementSellerQuery, LocalDateTime cutoffAt, int pageSize) {
        Assert.isTrue(pageSize >= 1, "pageSize는 1 이상이어야 합니다");
        this.settlementSellerQuery = requireNonNull(settlementSellerQuery, "settlementSellerQuery는 필수입니다");
        this.cutoffAt = requireNonNull(cutoffAt, "cutoffAt은 필수입니다");
        this.pageSize = pageSize;
    }

    // 덜 찬 페이지를 받으면 더 읽지 않는다
    @Override
    public Long read() {
        if (buffer.isEmpty() && !lastPage) {
            var sellerIds = settlementSellerQuery.findSellerIdsToSettle(cutoffAt, lastSellerId, pageSize);
            lastPage = sellerIds.size() < pageSize;
            if (!sellerIds.isEmpty()) {
                buffer.addAll(sellerIds);
                lastSellerId = sellerIds.getLast();
            }
        }
        return buffer.poll();
    }
}
