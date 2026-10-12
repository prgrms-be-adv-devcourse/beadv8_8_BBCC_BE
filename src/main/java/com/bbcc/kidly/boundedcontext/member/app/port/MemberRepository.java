package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
