package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.Notification;
import com.bbcc.kidly.boundedcontext.notification.domain.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 발송 지표(#1071). 채널별 발송 결과 카운터와, 아직 보내지 못한 알림의 수 · 가장 오래된 나이 게이지.
 * 실패율은 결과 카운터의 비율로, 적체는 게이지로 본다. 게이지는 수집할 때마다 상태 인덱스로 건수 · 최솟값만 읽는다.
 */
@Component
public class NotificationMetrics {

    static final String SENDS = "kidly.notification.sends";

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry, NotificationRepository notificationRepository) {
        this.registry = registry;
        Gauge.builder("kidly.notification.unsent", notificationRepository::countUnsent)
            .description("아직 보내지 못한 알림 수(PENDING · SENDING · 다시 시도할 FAILED)")
            .register(registry);
        Gauge.builder("kidly.notification.oldest.unsent.age.seconds", () -> notificationRepository
                .findOldestUnsentCreatedAt()
                .map(createdAt -> (double) Math.max(0, Duration.between(createdAt, LocalDateTime.now()).toSeconds()))
                .orElse(0.0))
            .description("아직 보내지 못한 알림 중 가장 오래된 것의 나이(초). 없으면 0")
            .register(registry);
    }

    /**
     * 발송 한 번이 끝난 알림의 결과를 센다. 결과는 sent · skipped · retry(다시 시도할 실패) · failed(더 시도하지 않는 실패).
     * 기록이 버전 충돌로 롤백되면 세지 않도록 커밋 뒤에 올린다. 그래서 상태를 기록하는 트랜잭션 안에서만 부른다.
     */
    public void recordResult(Notification notification) {
        String outcome = switch (notification.getStatus()) {
            case SENT -> "sent";
            case SKIPPED -> "skipped";
            case FAILED -> notification.willRetry() ? "retry" : "failed";
            case PENDING, SENDING -> null;
        };
        if (outcome == null) {
            return;
        }
        Counter counter = Counter.builder(SENDS)
            .description("알림 발송 결과 수(채널 · 결과별)")
            .tag("channel", notification.getChannel().name().toLowerCase(Locale.ROOT))
            .tag("outcome", outcome)
            .register(registry);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
