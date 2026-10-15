package com.bbcc.kidly.boundedcontext.settlement.out;

import static com.bbcc.kidly.boundedcontext.settlement.domain.QSettlementItem.settlementItem;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSettlementSellerQuery implements SettlementSellerQuery {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Long> findSellerIdsToSettle(LocalDateTime cutoffAt, Long afterSellerId, int limit) {
        return queryFactory
            .select(settlementItem.sellerId)
            .distinct()
            .from(settlementItem)
            .where(
                settlementItem.status.eq(SettlementItemStatus.PENDING),
                settlementItem.confirmedAt.lt(cutoffAt),
                settlementItem.sellerId.gt(afterSellerId))
            .orderBy(settlementItem.sellerId.asc())
            .limit(limit)
            .fetch();
    }
}
