package com.bbcc.kidly.boundedcontext.seller.domain;

import com.bbcc.kidly.member.support.deadletter.DeadLetter;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dead_letter", schema = "seller")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerDeadLetter extends DeadLetter {

    public SellerDeadLetter(DeadLetterMessage message) {
        super(message);
    }
}
