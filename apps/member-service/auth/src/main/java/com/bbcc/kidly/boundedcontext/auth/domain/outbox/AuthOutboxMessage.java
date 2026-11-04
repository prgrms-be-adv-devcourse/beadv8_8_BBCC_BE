package com.bbcc.kidly.boundedcontext.auth.domain.outbox;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "auth", name = "auth_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthOutboxMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private UUID eventId;
    @Column(nullable = false, length = 60)
    private String eventType;
    @Column(nullable = false, length = 100)
    private String messageKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    public static AuthOutboxMessage create(UUID eventId, String eventType, String messageKey, String payload) {
        var message = new AuthOutboxMessage();
        message.eventId = eventId;
        message.eventType = eventType;
        message.messageKey = messageKey;
        message.payload = payload;
        return message;
    }
}
