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

    // 요청의 sort는 무시하고 정산 종료일 최신순으로 고정한다
    @Override
    public Page<SettlementSummaryRow> findAll(SettlementSearch search, Pageable pageable) {
        var content = queryFactory
            .select(Projections.constructor(SettlementSummaryRow.class,
                settlement.id, settlement.periodStartOn, settlement.periodEndOn, settlement.totalSalesAmount,
                settlement.feeRate, settlement.feeAmount, settlement.settlementAmount,
                settlement.status.stringValue(), settlement.completedAt, settlement.paidAt))
            .from(settlement)
            .where(settlement.sellerId.eq(search.sellerId()), statusEq(search), endOnFrom(search), endOnTo(search))
            .orderBy(settlement.periodEndOn.desc(), settlement.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        var countQuery = queryFactory
            .select(settlement.count())
            .from(settlement)
            .where(settlement.sellerId.eq(search.sellerId()), statusEq(search), endOnFrom(search), endOnTo(search));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
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
