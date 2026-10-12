package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("""
        select m from Member m
        join m.socialAccounts s
        where s.provider = :provider and s.providerUserId = :providerUserId
        """)
    Optional<Member> findBySocialAccount(OAuthProvider provider, String providerUserId);
}
