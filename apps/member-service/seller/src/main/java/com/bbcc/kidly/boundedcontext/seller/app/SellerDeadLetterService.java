package com.bbcc.kidly.boundedcontext.seller.app;

import com.bbcc.kidly.boundedcontext.seller.app.port.SellerDeadLetterReplay;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetter;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetterRepository;
import com.bbcc.kidly.member.support.deadletter.DeadLetterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SellerDeadLetterService extends DeadLetterService<SellerDeadLetter> {

    public SellerDeadLetterService(SellerDeadLetterRepository repository, SellerDeadLetterReplay replayer,
        TransactionTemplate transactionTemplate) {
        super("판매자", repository, SellerDeadLetter::new, replayer, transactionTemplate);
    }
}
