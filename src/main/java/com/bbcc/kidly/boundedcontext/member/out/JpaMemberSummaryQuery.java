package com.bbcc.kidly.boundedcontext.member.out;

import static com.bbcc.kidly.boundedcontext.member.domain.QMember.member;

import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaMemberSummaryQuery implements MemberSummaryQuery {

    private final JPAQueryFactory queryFactory;

    // 정렬은 최신 가입부터(id 내림차순)로 고정한다
    @Override
    public Page<AdminMemberSummary> findAll(AdminMemberFilter filter, Pageable pageable) {
        var content = queryFactory
            .selectFrom(member)
            .where(listable(), filterBy(filter))
            .orderBy(member.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch()
            .stream()
            .map(AdminMemberSummary::from)
            .toList();
        var countQuery = queryFactory
            .select(member.count())
            .from(member)
            .where(listable(), filterBy(filter));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 관리자 · 탈퇴 회원은 관리 대상이 아니다
    private BooleanExpression listable() {
        return member.role.eq(MemberRole.USER).and(member.status.ne(MemberStatus.WITHDRAWN));
    }

    // ALL이면 조건에서 빠진다
    private BooleanExpression filterBy(AdminMemberFilter filter) {
        return switch (filter) {
            case ALL -> null;
            case BUYER -> member.sellerState.status.isNull();
            case SELLER -> member.sellerState.status.isNotNull();
            case SUSPENDED -> member.status.eq(MemberStatus.SUSPENDED);
        };
    }
}
