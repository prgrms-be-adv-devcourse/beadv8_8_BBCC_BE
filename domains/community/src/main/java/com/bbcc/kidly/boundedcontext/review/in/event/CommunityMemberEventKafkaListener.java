package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.in.config.CommunityKafkaListenerConfig;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kidly.member.events에서 member.withdrawn을 받아 Spring 수신과 같은 처리를 부른다. 다른 회원 이벤트는 건너뛴다.
 * 커뮤니티에서 이 토픽을 받는 리스너는 이것 하나다(찜 정리도 여기서 부르는 처리 안에서 한다).
 * 처리가 실패하면 예외를 그대로 던진다(재시도 → DLT). 본문을 읽지 못하면 재시도 없이 DLT로 간다.
 */
@Component
public class CommunityMemberEventKafkaListener {

    public static final String ID = "communityMemberEventKafkaListener";
    private static final String MEMBER_WITHDRAWN = "member.withdrawn";

    private final JsonMapper jsonMapper;
    private final CommunityMemberEventListener memberEventListener;

    public CommunityMemberEventKafkaListener(JsonMapper jsonMapper, CommunityMemberEventListener memberEventListener) {
        this.jsonMapper = jsonMapper;
        this.memberEventListener = memberEventListener;
    }

    @KafkaListener(id = ID, topics = CommunityKafkaListenerConfig.MEMBER_EVENTS,
        groupId = CommunityKafkaListenerConfig.GROUP, containerFactory = CommunityKafkaListenerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        if (MEMBER_WITHDRAWN.equals(CommunityKafkaMessages.eventType(record))) {
            memberEventListener.onMemberWithdrawn(jsonMapper.readValue(record.value(), MemberWithdrawnEvent.class));
        }
    }
}
