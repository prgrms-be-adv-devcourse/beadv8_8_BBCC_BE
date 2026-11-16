package com.bbcc.kidly.boundedcontext.seller;

import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxDispatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerOutboxSendException;
import com.bbcc.kidly.boundedcontext.seller.app.port.outbox.SellerOutboxSender;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxMessage;
import com.bbcc.kidly.boundedcontext.seller.domain.outbox.SellerOutboxRepository;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SellerOutboxDispatcherTest {

    @Test
    @DisplayName("보내지 못한 메시지에서 멈추고, 그 앞까지 보낸 것만 한 번에 기록한다")
    void stopsAtFailureAndRecordsOnlySent() {
        var repository = mock(SellerOutboxRepository.class);
        var sender = mock(SellerOutboxSender.class);
        var transactions = mock(TransactionTemplate.class);
        var first = mock(SellerOutboxMessage.class);
        var failing = mock(SellerOutboxMessage.class);
        var last = mock(SellerOutboxMessage.class);
        when(first.getId()).thenReturn(1L);
        doThrow(new SellerOutboxSendException("실패", new RuntimeException())).when(sender).send(failing);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());

        new SellerOutboxDispatcher(repository, sender, transactions).sendBatch(List.of(first, failing, last));

        verify(sender, never()).send(last);
        verify(repository).markSent(eq(List.of(1L)), any());
    }
}
