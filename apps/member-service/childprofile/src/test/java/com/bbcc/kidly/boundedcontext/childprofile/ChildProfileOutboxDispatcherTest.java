package com.bbcc.kidly.boundedcontext.childprofile;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxDispatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSendException;
import com.bbcc.kidly.boundedcontext.childprofile.app.port.outbox.ChildProfileEventSender;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxMessage;
import com.bbcc.kidly.boundedcontext.childprofile.domain.outbox.ChildProfileOutboxRepository;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ChildProfileOutboxDispatcherTest {

    @Test
    @DisplayName("보내지 못한 메시지에서 멈추고, 그 앞까지 보낸 것만 한 번에 기록한다")
    void stopsAtFailureAndRecordsOnlySent() {
        var repository = mock(ChildProfileOutboxRepository.class);
        var sender = mock(ChildProfileEventSender.class);
        var transactions = mock(TransactionTemplate.class);
        var first = mock(ChildProfileOutboxMessage.class);
        var failing = mock(ChildProfileOutboxMessage.class);
        var last = mock(ChildProfileOutboxMessage.class);
        when(first.getId()).thenReturn(1L);
        doThrow(new ChildProfileEventSendException("실패", new RuntimeException())).when(sender).send(failing);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());

        new ChildProfileOutboxDispatcher(repository, sender, transactions).sendBatch(List.of(first, failing, last));

        verify(sender, never()).send(last);
        verify(repository).markSent(eq(List.of(1L)), any());
    }
}
