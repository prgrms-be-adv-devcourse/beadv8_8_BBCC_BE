package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.order.OrderFlowEventRecorder;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 관리자가 판매자마다 판매자 변경됨을 다시 보내는 API를 HTTP 요청부터 응답까지 확인한다.
 * 나가는 이벤트는 커밋 뒤 이벤트를 모으는 빈으로 센다.
 */
@FunctionalTest
class AdminSellerChangedEventApiTest {

    private static final String PATH = "/api/v1/admin/sellers/changed-events";
    private static final Long ADMIN_ID = SellerFixture.ADMIN_ID;
    private static final Long ACTIVE_SELLER_ID = 1L;
    private static final Long SUSPENDED_SELLER_ID = 2L;
    private static final Long BUYER_ID = 3L;

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private OrderFlowEventRecorder eventRecorder;

    @Test
    @DisplayName("활성 · 정지 판매자마다 판매자 변경됨이 지금 상태로 한 번씩 나가고, 보낸 판매자 수를 돌려주며, 판매자는 그대로다")
    void republishAllSellers() {
        sellerRepository.save(SellerFixture.activeSeller(ACTIVE_SELLER_ID));
        var suspended = SellerFixture.activeSeller(SUSPENDED_SELLER_ID);
        suspended.suspend(ADMIN_ID, "허위 상품 등록 신고", LocalDateTime.now());
        sellerRepository.save(suspended);
        eventRecorder.clear();

        republish(TestMembers.admin(ADMIN_ID))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.publishedCount").isEqualTo(2);

        assertThat(eventRecorder.events(SellerChangedEvent.class))
            .extracting(event -> event.data().sellerId(), event -> event.data().businessName(),
                event -> event.data().status())
            .containsExactlyInAnyOrder(
                tuple(ACTIVE_SELLER_ID, SellerFixture.BUSINESS_NAME, SellerStatus.ACTIVE),
                tuple(SUSPENDED_SELLER_ID, SellerFixture.BUSINESS_NAME, SellerStatus.SUSPENDED));
        assertThat(sellerRepository.getById(ACTIVE_SELLER_ID).getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(sellerRepository.getById(SUSPENDED_SELLER_ID).getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"없음, 401", "판매자, 403", "구매자, 403"})
    @DisplayName("토큰이 없으면 401, 관리자가 아닌 회원(판매자 · 구매자)이면 403이고, 판매자 변경됨은 나가지 않는다")
    void rejectWithoutAdminRole(String member, int status) {
        sellerRepository.save(SellerFixture.activeSeller(ACTIVE_SELLER_ID));
        eventRecorder.clear();

        republish(headersOf(member)).expectStatus().isEqualTo(status);
        assertThat(eventRecorder.events(SellerChangedEvent.class)).isEmpty();
    }

    private Consumer<HttpHeaders> headersOf(String member) {
        return switch (member) {
            case "판매자" -> TestMembers.seller(ACTIVE_SELLER_ID);
            case "구매자" -> TestMembers.buyer(BUYER_ID);
            default -> null;
        };
    }

    private RestTestClient.ResponseSpec republish(Consumer<HttpHeaders> login) {
        return client.post().uri(PATH)
            .headers(login)
            .exchange();
    }
}
