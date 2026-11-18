package com.bbcc.kidly.boundedcontext.seller.domain.outbox;

import com.bbcc.kidly.member.support.outbox.OutboxMessage;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "seller", name = "seller_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerOutboxMessage extends OutboxMessage {

    public static final String TOPIC = "kidly.seller.events";

    public SellerOutboxMessage(CreateInfo info) {
        super(info);
    }
}
