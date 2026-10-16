package com.bbcc.kidly.boundedcontext.member.out;

import static com.bbcc.kidly.boundedcontext.member.domain.QMember.member;
import static com.bbcc.kidly.boundedcontext.member.domain.QSocialAccount.socialAccount;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JpaMemberRepositoryImpl {

    private final JPAQueryFactory queryFactory;

    public Optional<Member> findBySocialAccount(SocialIdentity identity) {
        return Optional.ofNullable(queryFactory
            .selectFrom(member)
            .join(member.socialAccounts, socialAccount)
            .where(
                socialAccount.identity.provider.eq(identity.provider()),
                socialAccount.identity.providerUserId.eq(identity.providerUserId()))
            .fetchOne());
    }
}
