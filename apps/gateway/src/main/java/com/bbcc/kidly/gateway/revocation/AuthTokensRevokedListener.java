package com.bbcc.kidly.gateway.revocation;

import com.bbcc.kidly.shared.auth.PrincipalId;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.auth.events에서 auth.tokens-revoked를 받아 무효화 목록에 넣는다. 다른 종류는 건너뛴다.
 * 같은 메시지를 다시 받아도 가장 늦은 시각만 남아 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokensRevokedListener implements ConsumerSeekAware {

    static final String LISTENER_ID = "gatewayAuthEventListener";

    // 이벤트 시각은 시간대 없는 한국 시간이다(팀 규칙)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AccessTokenRevocations revocations;
    private final JsonMapper jsonMapper;

    // 표식을 읽지 못하면 안전한 쪽으로 모두 되감는다. 표식은 쓰지 못하므로 Redis가 돌아오면 idle 확인이 다시 되감는다
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        try {
            rewindUnloaded(assignments.keySet(), callback);
        } catch (DataAccessException e) {
            log.warn("토큰 무효화 표식을 읽지 못해 받은 파티션을 모두 되감습니다. exception={}", e.getClass().getName());
            callback.seekToTimestamp(assignments.keySet(), rewindFrom());
        }
    }

    // Redis를 읽지 못하면 아무것도 하지 않는다(매초 되감지 않게). Redis가 돌아오면 다음 확인이 되감는다
    @Override
    public void onIdleContainer(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        try {
            rewindUnloaded(assignments.keySet(), callback);
        } catch (DataAccessException e) {
        }
    }

    @KafkaListener(id = LISTENER_ID, topics = GatewayKafkaListenerConfig.AUTH_EVENTS,
        groupId = GatewayKafkaListenerConfig.GROUP, containerFactory = "gatewayKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (GatewayKafkaListenerConfig.eventType(record)) {
            case "auth.tokens-revoked" -> apply(jsonMapper.readValue(record.value(), AuthTokensRevokedEvent.class));
            default -> {
                // Gateway가 받지 않는 종류
            }
        }
    }

    // data나 필드가 없으면 NullPointerException으로 DLT에 간다
    private void apply(AuthTokensRevokedEvent event) {
        var data = event.data();
        var principal = new PrincipalId(data.principalType(), data.principalId());
        revocations.revoke(principal, data.revokedAt().atZone(KST).toInstant(), Instant.now());
    }

    private void rewindUnloaded(Collection<TopicPartition> assigned, ConsumerSeekCallback callback) {
        var unloaded = assigned.stream().filter(p -> !revocations.isLoaded(p.partition())).toList();
        if (unloaded.isEmpty()) {
            return;
        }
        callback.seekToTimestamp(unloaded, rewindFrom());
        unloaded.forEach(p -> revocations.markLoaded(p.partition()));
        log.info("토큰 무효화 표식이 없는 파티션을 보존 기간 전부터 다시 읽습니다. partitions={}", unloaded);
    }

    private long rewindFrom() {
        return Instant.now().minus(revocations.retention()).toEpochMilli();
    }
}
