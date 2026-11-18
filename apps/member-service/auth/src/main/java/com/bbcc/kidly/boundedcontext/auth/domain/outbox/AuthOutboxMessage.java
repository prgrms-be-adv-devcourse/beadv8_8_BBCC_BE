package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "auth", name = "auth_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthOutboxMessage extends OutboxMessage {

    public static final String TOPIC = "kidly.auth.events";

    public AuthOutboxMessage(CreateInfo info) {
        super(info);
    }
}
