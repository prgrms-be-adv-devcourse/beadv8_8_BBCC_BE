package com.bbcc.kidly.boundedcontext.member.out;

import static com.bbcc.kidly.boundedcontext.member.domain.QMember.member;

import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberFilter;
import com.bbcc.kidly.boundedcontext.member.app.port.AdminMemberSummary;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberSummaryQuery;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.boundedcontext.member.domain.MemberStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JpaMemberSummaryQuery implements MemberSummaryQuery {

    // 19자리 이상은 Long을 넘칠 수 있어 닉네임 · 이메일로만 찾는다
    private static final Pattern ID_PATTERN = Pattern.compile("\\d{1,18}");

    private final JPAQueryFactory queryFactory;

    // 정렬은 최신 가입부터(id 내림차순)로 고정한다
    @Override
    public Page<AdminMemberSummary> findAll(AdminMemberFilter filter, String keyword, Pageable pageable) {
        var content = queryFactory
            .selectFrom(member)
            .where(listable(), filterBy(filter), keywordMatches(keyword))
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
            .where(listable(), filterBy(filter), keywordMatches(keyword));
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

    // 숫자만 있으면 회원 번호도 찾는다. 닉네임 · 이메일이 숫자를 담을 수 있어 OR로 묶는다
    private BooleanExpression keywordMatches(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        var stripped = keyword.strip();
        var textMatches = member.nickname.contains(stripped).or(member.email.contains(stripped));
        if (!ID_PATTERN.matcher(stripped).matches()) {
            return textMatches;
        }
        return member.id.eq(Long.valueOf(stripped)).or(textMatches);
    }
}
