package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.support.FunctionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀별 기준 옷 API를 HTTP 요청부터 응답까지 확인한다.
 * 자녀는 이벤트로 받아 둔 복사본으로만 알 수 있으므로, 복사본은 리스너가 부르는 서비스에 이벤트를 넘겨 만든다.
 * 자녀 1은 회원 1의 자녀다.
 */
@FunctionalTest
class ReferenceGarmentApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @BeforeEach
    void setUpChild() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    @Test
    @DisplayName("기준 옷이 없는 자녀의 목록을 조회하면 200과 빈 목록을 받는다")
    void listEmptyWhenNoReferenceGarment() {
        list(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    private static String path(Long childId) {
        return "/api/v1/recommendations/children/" + childId + "/reference-garments";
    }

    private RestTestClient.ResponseSpec list(Long memberId, Long childId) {
        return client.get().uri(path(childId))
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }
}
