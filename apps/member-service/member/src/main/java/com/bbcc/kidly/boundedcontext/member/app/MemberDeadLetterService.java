package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.port.MemberDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetter;
import com.bbcc.kidly.boundedcontext.member.domain.MemberDeadLetterRepository;
import com.bbcc.kidly.member.support.deadletter.DeadLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemberDeadLetterService extends DeadLetterService<MemberDeadLetter> {

    public MemberDeadLetterService(MemberDeadLetterRepository repository, MemberDeadLetterReplay replayer,
        TransactionTemplate transactionTemplate) {
        super("회원", repository, MemberDeadLetter::new, replayer, transactionTemplate);
    }
}
