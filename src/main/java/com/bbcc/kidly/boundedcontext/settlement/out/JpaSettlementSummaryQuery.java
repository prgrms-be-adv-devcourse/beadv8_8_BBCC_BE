package com.bbcc.kidly.boundedcontext.settlement.out;

import static com.bbcc.kidly.boundedcontext.settlement.domain.QSettlement.settlement;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSearch;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSummaryRow;
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
public class JpaSettlementSummaryQuery implements SettlementSummaryQuery {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<SettlementSummaryRow> findAll(SettlementSearch search, Pageable pageable) {
        var content = queryFactory
            .select(Projections.constructor(SettlementSummaryRow.class,
                settlement.id, settlement.sellerId, settlement.periodStartOn, settlement.periodEndOn,
                settlement.totalSalesAmount, settlement.feeRate, settlement.feeAmount, settlement.settlementAmount,
                settlement.status.stringValue(), settlement.completedAt, settlement.paidAt, settlement.failReason))
            .from(settlement)
            .where(matches(search))
            .orderBy(settlement.periodEndOn.desc(), settlement.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        var countQuery = queryFactory
            .select(settlement.count())
            .from(settlement)
            .where(matches(search));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression[] matches(SettlementSearch search) {
        return new BooleanExpression[] {
            sellerIdEq(search), statusEq(search), endOnFrom(search), endOnTo(search)};
    }

    private BooleanExpression sellerIdEq(SettlementSearch search) {
        return search.sellerId() == null ? null : settlement.sellerId.eq(search.sellerId());
    }

    private BooleanExpression statusEq(SettlementSearch search) {
        return search.status() == null ? null : settlement.status.eq(search.status());
    }

    private BooleanExpression endOnFrom(SettlementSearch search) {
        return search.fromOn() == null ? null : settlement.periodEndOn.goe(search.fromOn());
    }

    private BooleanExpression endOnTo(SettlementSearch search) {
        return search.toOn() == null ? null : settlement.periodEndOn.loe(search.toOn());
    }
}
