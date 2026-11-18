package com.bbcc.kidly.boundedcontext.member.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "member", name = "member_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOutboxMessage extends OutboxMessage {

    public static final String TOPIC = "kidly.member.events";

    public MemberOutboxMessage(CreateInfo info) {
        super(info);
    }
}
