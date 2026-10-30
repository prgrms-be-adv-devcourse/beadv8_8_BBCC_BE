package com.bbcc.kidly.gateway.revocation;

import com.bbcc.kidly.shared.member.event.MemberTokensRevokedEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.tokens-revoked를 받아 무효화 목록에 넣는다. 다른 종류는 건너뛴다.
 * 목록이 메모리라 Gateway가 다시 뜨면 빈다. 그래서 파티션을 받을 때마다 "지금 − 보존 기간(31분)"으로 되감아
 * 그동안의 무효화를 다시 읽는다. 그보다 오래된 무효화로 막을 토큰은 이미 만료됐다. 구독 그룹의 책갈피는 쓰지 않는다
 * (그 시각 뒤 메시지가 없는 파티션은 책갈피 · auto.offset.reset 위치에서 읽는다. 거기 있는 메시지는 보존 기간이 지나 목록에 남지 않는다).
 * 같은 메시지를 다시 받아도 가장 늦은 시각만 남아 결과가 같다. 예외는 잡지 않는다(재시도 없이 DLT, GatewayKafkaListenerConfig).
 */
@Component
@RequiredArgsConstructor
public class MemberTokensRevokedListener implements ConsumerSeekAware {

    // 이벤트 시각은 시간대 없는 한국 시간이다(팀 규칙)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AccessTokenRevocations revocations;
    private final JsonMapper jsonMapper;

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        var from = Instant.now().minus(revocations.retention());
        callback.seekToTimestamp(assignments.keySet(), from.toEpochMilli());
    }

    @KafkaListener(id = "gatewayMemberEventListener", topics = GatewayKafkaListenerConfig.MEMBER_EVENTS,
        groupId = GatewayKafkaListenerConfig.GROUP, containerFactory = "gatewayKafkaListenerContainerFactory")
    public void on(ConsumerRecord<String, String> record) {
        switch (GatewayKafkaListenerConfig.eventType(record)) {
            case "member.tokens-revoked" -> apply(jsonMapper.readValue(record.value(), MemberTokensRevokedEvent.class));
            default -> {
                // Gateway가 받지 않는 종류
            }
        }
    }

    // data나 필드가 없으면 NullPointerException으로 DLT에 간다
    private void apply(MemberTokensRevokedEvent event) {
        var data = event.data();
        revocations.revoke(data.memberId(), data.revokedAt().atZone(KST).toInstant(), Instant.now());
    }
}
