package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.deadletter.DeadLetter;
import com.bbcc.kidly.member.support.deadletter.DeadLetterMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dead_letter", schema = "auth")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthDeadLetter extends DeadLetter {

    public AuthDeadLetter(DeadLetterMessage message) {
        super(message);
    }
}
