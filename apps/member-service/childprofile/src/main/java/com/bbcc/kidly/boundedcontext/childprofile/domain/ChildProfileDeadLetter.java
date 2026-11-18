package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.member.support.deadletter.DeadLetter;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dead_letter", schema = "childprofile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfileDeadLetter extends DeadLetter {

    public ChildProfileDeadLetter(DeadLetterMessage message) {
        super(message);
    }
}
