package com.bbcc.kidly.boundedcontext.settlement.out;

import static com.bbcc.kidly.boundedcontext.settlement.domain.QSettlementItem.settlementItem;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSettlementTargetQuery implements SettlementTargetQuery {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<SettlementTargetRow> findPending(Long sellerId, Pageable pageable) {
        var content = queryFactory
            .select(Projections.constructor(SettlementTargetRow.class,
                settlementItem.orderItemId, settlementItem.orderId, settlementItem.salesAmount,
                settlementItem.confirmedAt))
            .from(settlementItem)
            .where(pending(sellerId))
            .orderBy(settlementItem.confirmedAt.desc(), settlementItem.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        var countQuery = queryFactory
            .select(settlementItem.count())
            .from(settlementItem)
            .where(pending(sellerId));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression pending(Long sellerId) {
        return settlementItem.sellerId.eq(sellerId).and(settlementItem.status.eq(SettlementItemStatus.PENDING));
    }
}
