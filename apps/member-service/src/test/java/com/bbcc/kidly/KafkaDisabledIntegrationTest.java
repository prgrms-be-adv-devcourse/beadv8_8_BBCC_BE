package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.outbox.AuthOutboxRelay;
import com.bbcc.kidly.boundedcontext.auth.in.outbox.AuthOutboxScheduler;
import com.bbcc.kidly.boundedcontext.childprofile.app.outbox.ChildProfileOutboxRelay;
import com.bbcc.kidly.boundedcontext.childprofile.in.outbox.ChildProfileOutboxScheduler;
import com.bbcc.kidly.boundedcontext.member.app.outbox.MemberOutboxRelay;
import com.bbcc.kidly.boundedcontext.member.in.outbox.MemberOutboxScheduler;
import com.bbcc.kidly.boundedcontext.seller.app.outbox.SellerOutboxRelay;
import com.bbcc.kidly.boundedcontext.seller.in.outbox.SellerOutboxScheduler;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.testcontainers.kafka.KafkaContainer;

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
    @DisplayName("일반 통합 테스트에서는 인증 아웃박스 릴레이와 스케줄러를 만들지 않는다")
    void noAuthOutboxRelay() {
        assertThat(context.getBeanNamesForType(AuthOutboxRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthOutboxScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 회원 아웃박스 릴레이와 그 스케줄러를 만들지 않는다")
    void noMemberOutboxRelay() {
        assertThat(context.getBeanNamesForType(MemberOutboxRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MemberOutboxScheduler.class)).isEmpty();
    }

    @Test
    @DisplayName("일반 통합 테스트에서는 판매자 아웃박스 릴레이와 스케줄러를 만들지 않는다")
    void noSellerOutboxRelay() {
        assertThat(context.getBeanNamesForType(SellerOutboxRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SellerOutboxScheduler.class)).isEmpty();
    }
    @Test
    @DisplayName("일반 통합 테스트에서는 자녀 아웃박스 릴레이와 스케줄러를 만들지 않는다")
    void noChildProfileOutboxRelay() {
        assertThat(context.getBeanNamesForType(ChildProfileOutboxRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ChildProfileOutboxScheduler.class)).isEmpty();
    }
}
