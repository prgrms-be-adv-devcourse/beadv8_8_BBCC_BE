package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelayService;
import com.bbcc.kidly.boundedcontext.childprofile.in.outbox.ChildProfileOutboxScheduler;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelayService;
import com.bbcc.kidly.boundedcontext.member.in.outbox.MemberOutboxScheduler;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka가 필요 없는 통합 테스트는 Kafka 없이 돈다(회의 10-29 안건 3).
 * Kafka 컨테이너는 @KafkaIntegrationTest에서만 뜨고, 커밋 뒤 Kafka로 보내는 빈은 만들지 않는다(kidly.kafka.producer.enabled).
 */
@IntegrationTest
class KafkaDisabledIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("일반 통합 테스트에는 Kafka 컨테이너가 뜨지 않는다")
    void noKafkaContainer() {
        assertThat(context.getBeanNamesForType(KafkaContainer.class)).isEmpty();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 결제 아웃박스 릴레이 스케줄러를 만들지 않는다")
    void noKafkaRelay() {
        assertThat(context.containsBean("paymentOutboxRelayScheduler")).isFalse();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 커밋 뒤 Kafka로 보내는 회원 빈을 만들지 않는다")
    void noMemberKafkaRelay() {
        assertThat(context.containsBean("memberEventRelay")).isFalse();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 회원 아웃박스 릴레이와 그 스케줄러를 만들지 않는다")
    void noMemberOutboxRelay() {
        assertThat(context.getBeanNamesForType(MemberOutboxRelayService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MemberOutboxScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 커밋 뒤 Kafka로 보내는 판매자 빈을 만들지 않는다")
    void noSellerKafkaRelay() {
        assertThat(context.containsBean("sellerEventRelay")).isFalse();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 자녀 프로필 아웃박스 릴레이와 그 스케줄러를 만들지 않는다")
    void noChildProfileOutboxRelay() {
        assertThat(context.getBeanNamesForType(ChildProfileOutboxRelayService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ChildProfileOutboxScheduler.class)).isEmpty();
    }
}
