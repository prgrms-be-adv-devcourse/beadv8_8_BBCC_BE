package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxProperties;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSender;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxMessage;
import com.bbcc.kidly.boundedcontext.product.domain.outbox.ProductOutboxRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductOutboxRelayServiceTest {

    private static final ProductOutboxProperties PROPERTIES = new ProductOutboxProperties(100, Duration.ofDays(7));

    @Mock
    private ProductOutboxRepository outboxRepository;

    @Mock
    private ProductEventSender sender;

    @Test
    @DisplayName("보내지 않은 메시지를 받은 순서대로 보내고, 하나를 보낼 때마다 보낸 시각과 시도 1회를 저장한다")
    void sendInOrderAndSaveEach() {
        ProductOutboxMessage first = message();
        ProductOutboxMessage second = message();
        given(outboxRepository.findUnsent(100)).willReturn(List.of(first, second));

        relayService().relay();

        InOrder order = inOrder(sender, outboxRepository);
        order.verify(sender).send(first);
        order.verify(outboxRepository).save(first);
        order.verify(sender).send(second);
        order.verify(outboxRepository).save(second);
        assertThat(List.of(first, second)).allSatisfy(message -> {
            assertThat(message.getSentAt()).isNotNull();
            assertThat(message.getAttemptCount()).isEqualTo(1);
        });
    }

    private ProductOutboxRelayService relayService() {
        return new ProductOutboxRelayService(outboxRepository, sender, PROPERTIES);
    }

    private static ProductOutboxMessage message() {
        return ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(UUID.randomUUID()));
    }
}
