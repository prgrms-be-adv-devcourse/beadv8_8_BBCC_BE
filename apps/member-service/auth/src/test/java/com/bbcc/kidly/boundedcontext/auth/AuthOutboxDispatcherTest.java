package com.bbcc.kidly.boundedcontext.auth;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxDispatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSendException;
import com.bbcc.kidly.boundedcontext.auth.app.port.outbox.AuthOutboxSender;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxMessage;
import com.bbcc.kidly.boundedcontext.auth.domain.outbox.AuthOutboxRepository;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AuthOutboxDispatcherTest {

    @Test
    @DisplayName("보내지 못한 메시지에서 멈추고, 그 앞까지 보낸 것만 한 번에 기록한다")
    void stopsAtFailureAndRecordsOnlySent() {
        var repository = mock(AuthOutboxRepository.class);
        var sender = mock(AuthOutboxSender.class);
        var transactions = mock(TransactionTemplate.class);
        var first = mock(AuthOutboxMessage.class);
        var failing = mock(AuthOutboxMessage.class);
        var last = mock(AuthOutboxMessage.class);
        when(first.getId()).thenReturn(1L);
        doThrow(new AuthOutboxSendException("실패")).when(sender).send(failing);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());

        new AuthOutboxDispatcher(repository, sender, transactions).sendBatch(List.of(first, failing, last));

        verify(sender, never()).send(last);
        verify(repository).markSent(eq(List.of(1L)), any());
    }
}
