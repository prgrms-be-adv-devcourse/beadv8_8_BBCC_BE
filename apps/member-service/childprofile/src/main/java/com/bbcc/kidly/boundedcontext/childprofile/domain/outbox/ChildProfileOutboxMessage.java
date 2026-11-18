package com.bbcc.kidly.boundedcontext.childprofile.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "childprofile", name = "child_profile_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfileOutboxMessage extends OutboxMessage {

    public static final String TOPIC = "kidly.childprofile.events";

    public ChildProfileOutboxMessage(CreateInfo info) {
        super(info);
    }
}
