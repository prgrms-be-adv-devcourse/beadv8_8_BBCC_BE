package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.ChildProfileDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetter;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
import com.bbcc.kidly.member.support.deadletter.DeadLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChildProfileDeadLetterService extends DeadLetterService<ChildProfileDeadLetter> {

    public ChildProfileDeadLetterService(ChildProfileDeadLetterRepository repository,
        ChildProfileDeadLetterReplay replayer, TransactionTemplate transactionTemplate) {
        super("자녀 프로필", repository, ChildProfileDeadLetter::new, replayer, transactionTemplate);
    }
}
