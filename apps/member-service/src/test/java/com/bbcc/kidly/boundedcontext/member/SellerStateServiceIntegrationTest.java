package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.SellerStateService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.shared.auth.event.AuthTokensRevokedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.CommittedEventRecorder;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class SellerStateServiceIntegrationTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private SellerStateService sellerStateService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommittedEventRecorder recorder;

    @Test
    @DisplayName("같은 판매자 정지 이벤트를 동시에 처리해도 토큰 무효화는 한 번만 발행한다")
    void concurrentSuspension() throws Exception {
        var sellerId = memberRepository.save(MemberFixture.withSellerState(SellerStatus.ACTIVE)).getId();
        var suspendedAt = MemberFixture.NOW.plusDays(1);
        recorder.clear();
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            var futures = new ArrayList<Future<?>>();
            for (var i = 0; i < THREAD_COUNT; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    sellerStateService.apply(sellerId, SellerStatus.SUSPENDED, suspendedAt);
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(recorder.events(AuthTokensRevokedEvent.class))
            .singleElement()
            .satisfies(event -> assertThat(event.data().principalId()).isEqualTo(sellerId));
    }
}
