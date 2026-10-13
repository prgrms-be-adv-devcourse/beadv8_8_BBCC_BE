package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findBySocialAccount(OAuthProvider provider, String providerUserId);
}
