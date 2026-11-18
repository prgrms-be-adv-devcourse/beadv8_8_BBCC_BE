package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.deadletter.DeadLetterRepository;

public interface AuthDeadLetterRepository extends DeadLetterRepository<AuthDeadLetter> {
}
