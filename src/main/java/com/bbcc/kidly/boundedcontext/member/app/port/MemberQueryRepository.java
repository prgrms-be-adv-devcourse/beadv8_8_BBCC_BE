package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import java.util.Optional;

public interface MemberQueryRepository {

    Optional<Member> findBySocialAccount(OAuthProvider provider, String providerUserId);
}
