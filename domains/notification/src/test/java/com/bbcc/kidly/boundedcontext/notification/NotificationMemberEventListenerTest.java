package com.bbcc.kidly.boundedcontext.notification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.app.NotificationService;
import com.bbcc.kidly.boundedcontext.notification.app.dto.WelcomeEmailCommand;
import com.bbcc.kidly.boundedcontext.notification.in.event.NotificationMemberEventListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationMemberEventListenerTest {

    private static final Long MEMBER_ID = 7L;
    private static final String EMAIL = "parent@kidly.co.kr";
    private static final String EVENT_ID = "6f1c2e4a-0b7d-4c1e-9a55-2d3f8e7b1a90";

    @Mock
    private NotificationService notificationService;

    @Mock
    private MemberContactService memberContactService;

    private NotificationMemberEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationMemberEventListener(notificationService, memberContactService,
            JsonMapper.builder().build());
    }

    @Test
    @DisplayName("resent 필드가 없는 예전 가입 이벤트는 새 가입으로 읽어 복사본을 저장하고 환영 메일을 만든다")
    void registeredWithoutResent() {
        listener.on(registered("\"memberId\":7,\"email\":\"" + EMAIL + "\""));

        then(memberContactService).should().save(MEMBER_ID, EMAIL);
        var captor = ArgumentCaptor.forClass(WelcomeEmailCommand.class);
        then(notificationService).should().createWelcomeEmail(captor.capture());
        assertThat(captor.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("다시 보낸 가입 이벤트(resent)는 복사본만 저장하고 환영 메일은 만들지 않는다")
    void resentRegistered() {
        listener.on(registered("\"memberId\":7,\"email\":\"" + EMAIL + "\",\"resent\":true"));

        then(memberContactService).should().save(MEMBER_ID, EMAIL);
        then(notificationService).should(never()).createWelcomeEmail(any());
    }

    private static ConsumerRecord<String, String> registered(String data) {
        var body = "{\"eventId\":\"" + EVENT_ID + "\",\"occurredAt\":\"2026-11-06T09:00:00\",\"data\":{" + data + "}}";
        var record = new ConsumerRecord<>("kidly.member.events", 0, 0L, String.valueOf(MEMBER_ID), body);
        record.headers().add("eventType", "member.registered".getBytes(UTF_8));
        return record;
    }
}
