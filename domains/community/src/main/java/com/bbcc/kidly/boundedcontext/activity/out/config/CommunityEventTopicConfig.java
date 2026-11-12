package com.bbcc.kidly.boundedcontext.activity.out.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 커뮤니티가 보내는 토픽 kidly.community.events(파티션 3 · 복제 1, 만든 뒤 바꾸지 않는다). 브로커 자동 생성은 꺼져 있어 보내는 쪽이 만든다.
 */
@Configuration
public class CommunityEventTopicConfig {

    @Bean
    NewTopic communityEventsTopic() {
        return TopicBuilder.name("kidly.community.events").partitions(3).replicas(1).build();
    }
}
