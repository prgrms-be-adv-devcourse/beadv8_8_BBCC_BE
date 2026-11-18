package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.port.AuthDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetter;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthDeadLetterRepository;
import com.bbcc.kidly.member.support.deadletter.DeadLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthDeadLetterService extends DeadLetterService<AuthDeadLetter> {

    public AuthDeadLetterService(AuthDeadLetterRepository repository, AuthDeadLetterReplay replayer,
        TransactionTemplate transactionTemplate) {
        super("인증", repository, AuthDeadLetter::new, replayer, transactionTemplate);
    }
}
