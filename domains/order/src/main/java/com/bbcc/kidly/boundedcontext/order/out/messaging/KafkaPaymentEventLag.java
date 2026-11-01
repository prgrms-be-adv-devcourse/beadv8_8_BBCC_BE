package com.bbcc.kidly.boundedcontext.order.out.messaging;

import com.bbcc.kidly.boundedcontext.order.app.port.PaymentEventLag;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * 구독 그룹 kidly.order가 kidly.payment.events에서 처리하지 않은 메시지 수를 Kafka에서 읽는다(#561).
 * 파티션마다 끝 오프셋 − 그룹 커밋 오프셋을 더한다. 확인 전체를 2초 안에 끝내고, 넘기거나 실패하면 CHECK_FAILED.
 * Boot KafkaAdmin의 설정(앱 spring.kafka.admin과 연결 주소)으로 AdminClient를 처음 확인할 때 하나 만들어 계속 쓰고 앱 종료 때 닫는다.
 * 빈으로 등록하지 않으므로 Boot KafkaAdmin과 다른 도메인에 영향이 없다. 처음 확인 전에는 Kafka에 붙지 않는다(Kafka 없는 테스트 · 기동).
 */
@Component
class KafkaPaymentEventLag implements PaymentEventLag, DisposableBean {

    static final String TOPIC = "kidly.payment.events";
    static final String GROUP = "kidly.order";

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final Map<String, Object> adminProperties;
    private Admin admin;

    @Autowired
    KafkaPaymentEventLag(KafkaAdmin kafkaAdmin) {
        this(kafkaAdmin.getConfigurationProperties());
    }

    KafkaPaymentEventLag(Map<String, Object> adminProperties) {
        Map<String, Object> properties = new HashMap<>(adminProperties);
        // 요청 하나가 제한 시간보다 오래 재시도하지 않게 한다
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        this.adminProperties = Map.copyOf(properties);
    }

    @Override
    public PaymentEventLag.Check check() {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        try {
            Admin client = admin();
            List<TopicPartition> partitions = await(client.describeTopics(List.of(TOPIC)).allTopicNames(), deadline)
                .get(TOPIC).partitions().stream()
                .map(info -> new TopicPartition(TOPIC, info.partition()))
                .toList();
            Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
            partitions.forEach(partition -> latest.put(partition, OffsetSpec.latest()));
            Map<Integer, Long> ends = new HashMap<>();
            await(client.listOffsets(latest).all(), deadline)
                .forEach((partition, info) -> ends.put(partition.partition(), info.offset()));
            Map<Integer, Long> committed = new HashMap<>();
            Map<TopicPartition, OffsetAndMetadata> groupOffsets = await(
                client.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata(), deadline);
            groupOffsets.forEach((partition, offset) -> {
                if (partition.topic().equals(TOPIC) && offset != null) {
                    committed.put(partition.partition(), offset.offset());
                }
            });
            return evaluate(ends, committed);
        } catch (TimeoutException e) {
            return PaymentEventLag.Check.checkFailed("timeout");
        } catch (ExecutionException e) {
            return PaymentEventLag.Check.checkFailed(reasonOf(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PaymentEventLag.Check.checkFailed("interrupted");
        } catch (RuntimeException e) {
            return PaymentEventLag.Check.checkFailed(reasonOf(e));
        }
    }

    /**
     * 파티션 번호별 끝 오프셋과 그룹 커밋 오프셋으로 결과를 정한다. 커밋이 없는 파티션은 비어 있으면(끝 0) 넘어가고,
     * 읽을 메시지가 있으면 배정 · 첫 처리 전으로 본다.
     */
    static PaymentEventLag.Check evaluate(Map<Integer, Long> ends, Map<Integer, Long> committed) {
        long lag = 0;
        boolean notAssigned = false;
        for (Map.Entry<Integer, Long> end : ends.entrySet()) {
            Long position = committed.get(end.getKey());
            if (position == null) {
                notAssigned |= end.getValue() > 0;
                continue;
            }
            lag += Math.max(0, end.getValue() - position);
        }
        if (notAssigned) {
            return PaymentEventLag.Check.notAssigned();
        }
        return lag == 0 ? PaymentEventLag.Check.caughtUp() : PaymentEventLag.Check.lagging(lag);
    }

    @Override
    public synchronized void destroy() {
        if (admin != null) {
            admin.close(Duration.ofSeconds(1));
            admin = null;
        }
    }

    private synchronized Admin admin() {
        if (admin == null) {
            admin = Admin.create(adminProperties);
        }
        return admin;
    }

    private static <T> T await(KafkaFuture<T> future, long deadline)
        throws ExecutionException, InterruptedException, TimeoutException {
        long remaining = Math.max(0, deadline - System.nanoTime());
        return future.get(remaining, TimeUnit.NANOSECONDS);
    }

    // 로그에 남길 짧은 이유 코드: 원인 예외 종류
    private static String reasonOf(Throwable error) {
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }
}
