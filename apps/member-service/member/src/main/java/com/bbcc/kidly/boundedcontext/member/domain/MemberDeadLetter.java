package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.member.support.deadletter.DeadLetter;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dead_letter", schema = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDeadLetter extends DeadLetter {

    public MemberDeadLetter(DeadLetterMessage message) {
        super(message);
    }
}
