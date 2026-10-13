package com.bbcc.kidly.boundedcontext.seller.out;

import static com.bbcc.kidly.boundedcontext.seller.domain.QSellerApplication.sellerApplication;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummary;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerApplicationSummaryQuery;
import com.bbcc.kidly.shared.seller.domain.SellerApplicationStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSellerApplicationSummaryQuery implements SellerApplicationSummaryQuery {

    private final JPAQueryFactory queryFactory;

    // 정렬은 최신 신청부터(id 내림차순)로 고정한다
    @Override
    public Page<SellerApplicationSummary> findAll(SellerApplicationStatus status, Pageable pageable) {
        var content = queryFactory
            .selectFrom(sellerApplication)
            .where(statusEq(status))
            .orderBy(sellerApplication.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch()
            .stream()
            .map(SellerApplicationSummary::from)
            .toList();
        var countQuery = queryFactory
            .select(sellerApplication.count())
            .from(sellerApplication)
            .where(statusEq(status));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // null이면 조건에서 빠진다
    private BooleanExpression statusEq(SellerApplicationStatus status) {
        return status == null ? null : sellerApplication.status.eq(status);
    }
}
