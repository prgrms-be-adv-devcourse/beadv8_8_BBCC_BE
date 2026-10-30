package com.bbcc.kidly.boundedcontext.payment.out.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSendException;
import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentEventSender;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentOutboxMessage;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 아웃박스 메시지를 저장된 토픽 · 키 · 본문 그대로 Kafka로 보낸다(#550). 헤더는 eventType · eventId 두 개만 붙인다.
 * 릴레이가 결과를 보고 보낸 시각이나 실패를 기록하므로, 브로커 응답을 sendTimeout까지 기다린다.
 */
public class KafkaPaymentEventSender implements PaymentEventSender, DisposableBean {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration sendTimeout;

    public KafkaPaymentEventSender(KafkaTemplate<String, String> kafkaTemplate, Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void send(PaymentOutboxMessage message) {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());
        record.headers()
            .add("eventType", message.getEventType().getBytes(UTF_8))
            .add("eventId", message.getEventId().toString().getBytes(UTF_8));
        try {
            kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw failure(message, "응답 대기 시간 %dms 초과".formatted(sendTimeout.toMillis()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure(message, "응답 대기 중 인터럽트", e);
        } catch (ExecutionException | RuntimeException e) {
            // 보내기 전 실패는 "Send failed", 보낸 뒤 실패는 "Failed to send"로 Spring이 감싸 원인이 없으므로 Kafka 클라이언트 예외를 꺼낸다
            Throwable cause = kafkaClientCause(e);
            throw failure(message, cause.getMessage(), cause);
        }
    }

    // 결제 전용 템플릿은 빈이 아니라 Spring이 닫아 주지 않으므로, 앱이 내려갈 때 직접 닫는다
    @Override
    public void destroy() {
        kafkaTemplate.destroy();
    }

    // 예외 메시지는 로그 · last_error에 남으므로 본문 대신 eventType · eventId · 키와 원인만 담는다
    private static PaymentEventSendException failure(PaymentOutboxMessage message, String reason, Throwable cause) {
        return new PaymentEventSendException(
            "결제 이벤트를 Kafka로 보냈는지 확인하지 못했습니다. eventType=%s eventId=%s key=%s reason=%s"
                .formatted(message.getEventType(), message.getEventId(), message.getMessageKey(), reason),
            cause);
    }

    // 원인 사슬에서 처음 나오는 Kafka 클라이언트 예외. 무엇을 얼마나 기다렸는지가 메시지에 있다. 없으면 받은 예외 그대로
    private static Throwable kafkaClientCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getName().startsWith("org.apache.kafka.")) {
                return cause;
            }
        }
        return error;
    }
}
