package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxProperties;
import com.bbcc.kidly.boundedcontext.product.app.outbox.ProductOutboxRelayService;
import com.bbcc.kidly.boundedcontext.product.app.port.outbox.ProductEventSendException;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ProductOutboxRelayServiceTest {

    private static final ProductOutboxProperties PROPERTIES = new ProductOutboxProperties(100, Duration.ofDays(7));

    @Mock
    private ProductOutboxRepository outboxRepository;

    @Mock
    private ProductEventSender sender;

    @Test
    @DisplayName("보내지 않은 메시지를 받은 순서대로 보내고, 하나를 보낼 때마다 보낸 시각과 시도 1회를 저장한 뒤 보낸 건수를 남긴다")
    void sendInOrderAndSaveEach(CapturedOutput output) {
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
        assertThat(output.getAll().lines())
            .anyMatch(line -> line.contains("INFO") && line.contains("상품 아웃박스 메시지를 보냈습니다. sent=2"));
    }

    @Test
    @DisplayName("보낼 메시지가 없으면 보낸 건수 로그를 남기지 않는다")
    void logNothingWhenNoMessage(CapturedOutput output) {
        given(outboxRepository.findUnsent(100)).willReturn(List.of());

        relayService().relay();

        assertThat(output.getAll()).doesNotContain("상품 아웃박스 메시지를 보냈습니다");
    }

    @Test
    @DisplayName("보내다 실패하면 그 메시지에 시도 1회와 오류를 저장하고 멈춘다. 로그에는 예외 메시지와 그때까지 보낸 건수만 남기고 본문은 남기지 않는다")
    void recordFailureAndStop(CapturedOutput output) {
        ProductOutboxMessage first = message();
        ProductOutboxMessage failing = message();
        ProductOutboxMessage after = message();
        given(outboxRepository.findUnsent(100)).willReturn(List.of(first, failing, after));
        String error = "상품 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. eventType=product.changed eventId="
            + failing.getEventId() + " key=1 reason=broker down";
        // 다른 메시지를 보낼 때 이 스텁과 인자가 달라도 정상이다(엄격 스텁이 인자 불일치로 보지 않게)
        lenient().doThrow(new ProductEventSendException(error, new IllegalStateException("broker down")))
            .when(sender).send(failing);

        assertThatCode(() -> relayService().relay()).doesNotThrowAnyException();

        verify(outboxRepository).save(first);
        verify(outboxRepository).save(failing);
        verify(sender, never()).send(after);
        verify(outboxRepository, never()).save(after);
        assertThat(failing.getSentAt()).isNull();
        assertThat(failing.getAttemptCount()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo(error);
        assertThat(after.getAttemptCount()).isZero();
        assertThat(output.getAll().lines())
            .anyMatch(line -> line.contains("ERROR") && line.contains(error))
            .anyMatch(line -> line.contains("INFO") && line.contains("상품 아웃박스 메시지를 보냈습니다. sent=1"));
        assertThat(output.getAll()).doesNotContain(ProductOutboxMessageFixture.PAYLOAD);
    }

    @Test
    @DisplayName("첫 메시지부터 보내지 못하면 실패만 ERROR로 남기고 보낸 건수 로그는 남기지 않는다")
    void logNoSentCountWhenFirstMessageFails(CapturedOutput output) {
        ProductOutboxMessage failing = message();
        given(outboxRepository.findUnsent(100)).willReturn(List.of(failing));
        willThrow(new ProductEventSendException("broker down", new IllegalStateException("broker down")))
            .given(sender).send(failing);

        relayService().relay();

        assertThat(output.getAll().lines()).anyMatch(line -> line.contains("ERROR") && line.contains("broker down"));
        assertThat(output.getAll()).doesNotContain("상품 아웃박스 메시지를 보냈습니다");
    }

    @Test
    @DisplayName("보낸 결과를 저장하다 DB 오류로 끝나도, 그때까지 보낸 건수는 남기고 예외는 스케줄러로 넘긴다")
    void logSentCountEvenWhenSaveFails(CapturedOutput output) {
        ProductOutboxMessage first = message();
        ProductOutboxMessage second = message();
        given(outboxRepository.findUnsent(100)).willReturn(List.of(first, second));
        // 첫 메시지를 저장할 때 이 스텁과 인자가 달라도 정상이다(엄격 스텁이 인자 불일치로 보지 않게)
        lenient().doThrow(new IllegalStateException("DB 연결 실패")).when(outboxRepository).save(second);

        assertThatThrownBy(() -> relayService().relay()).isInstanceOf(IllegalStateException.class);

        assertThat(output.getAll().lines())
            .anyMatch(line -> line.contains("INFO") && line.contains("상품 아웃박스 메시지를 보냈습니다. sent=1"));
    }

    @Test
    @DisplayName("실패를 저장하다 DB 오류가 나도 Kafka 전송 실패 원인은 ERROR로 남긴다")
    void logSendFailureBeforeSavingIt(CapturedOutput output) {
        ProductOutboxMessage failing = message();
        given(outboxRepository.findUnsent(100)).willReturn(List.of(failing));
        willThrow(new ProductEventSendException("broker down", new IllegalStateException("broker down")))
            .given(sender).send(failing);
        willThrow(new IllegalStateException("DB 연결 실패")).given(outboxRepository).save(failing);

        assertThatThrownBy(() -> relayService().relay()).isInstanceOf(IllegalStateException.class);

        assertThat(output.getAll().lines()).anyMatch(line -> line.contains("ERROR") && line.contains("broker down"));
    }

    private ProductOutboxRelayService relayService() {
        return new ProductOutboxRelayService(outboxRepository, sender, PROPERTIES);
    }

    private static ProductOutboxMessage message() {
        return ProductOutboxMessage.create(ProductOutboxMessageFixture.productChanged(UUID.randomUUID()));
    }
}
