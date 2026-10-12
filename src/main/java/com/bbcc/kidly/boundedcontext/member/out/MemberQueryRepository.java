package com.bbcc.kidly.boundedcontext.member.out;

import static com.bbcc.kidly.boundedcontext.member.domain.QMember.member;
import static com.bbcc.kidly.boundedcontext.member.domain.QSocialAccount.socialAccount;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Optional<Member> findBySocialAccount(OAuthProvider provider, String providerUserId) {
        return Optional.ofNullable(queryFactory
            .selectFrom(member)
            .join(member.socialAccounts, socialAccount)
            .where(socialAccount.provider.eq(provider), socialAccount.providerUserId.eq(providerUserId))
            .fetchOne());
    }
}
