package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_NAME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productReplicaRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.BUSINESS_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class RecommendedProductServiceTest {

    private static final int FIRST_PAGE = 0;

    private static final int PAGE_SIZE = 20;

    private static final Long OLDEST_PRODUCT_ID = 1L;

    private static final Long NEWEST_PRODUCT_ID = 2L;

    private static final Long MIDDLE_PRODUCT_ID = 3L;

    /**
     * 판매자 복사본이 없는 판매자의 ID다. 상품 A의 판매자 ID와 다른 값을 쓴다.
     */
    private static final Long OTHER_SELLER_ID = 61L;

    @Mock
    private RecommendReplicaProductRepository replicaProductRepository;

    @Mock
    private RecommendReplicaChildRepository replicaChildRepository;

    @Mock
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Captor
    private ArgumentCaptor<Iterable<Long>> sellerIdsCaptor;

    private RecommendedProductService service;

    // 서비스가 체형표로 사이즈 계산기를 만들므로 @InjectMocks 대신 직접 만든다
    @BeforeEach
    void setUp() {
        service = new RecommendedProductService(
            replicaProductRepository, replicaChildRepository, replicaSellerRepository,
            RecommendFixture.growthChart());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableChildren")
    @DisplayName("회원이 볼 수 없는 자녀면 상품을 읽지 않고 자녀를 찾을 수 없다고 응답한다")
    void rejectUnavailableChild(Optional<RecommendReplicaChild> foundChild, Long memberId) {
        given(replicaChildRepository.findById(CHILD_1_ID)).willReturn(foundChild);

        assertThatThrownBy(() -> service.list(memberId, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, FIRST_PAGE, PAGE_SIZE))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(RecommendErrorCode.CHILD_NOT_FOUND);
        verifyNoInteractions(replicaProductRepository, replicaSellerRepository);
    }

    @Test
    @DisplayName("맞는 상품을 신상품순으로 page · size만큼 잘라 첫 페이지를 돌려주고 다음 페이지가 있다고 알린다")
    void firstPageOfNewestProducts() {
        givenVisibleChildAndThreeProducts();

        Page<RecommendedProductResult> result =
            service.list(MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, FIRST_PAGE, 2);

        // 자녀는 키 115cm · 몸무게 20.0kg · 체형 보통이고 선호 핏이 여유라, 110과 120 두 후보 중 큰 120을 추천한다
        // 세 상품 모두 옵션 110과 120의 판매가가 25,000원이라 최저가도 25,000원이다
        assertThat(result.getContent())
            .extracting(RecommendedProductResult::productId, RecommendedProductResult::name,
                RecommendedProductResult::thumbnailUrl, RecommendedProductResult::minPrice,
                RecommendedProductResult::recommendedSize)
            .containsExactly(
                tuple(NEWEST_PRODUCT_ID, PRODUCT_A_NAME, PRODUCT_A_THUMBNAIL_URL, 25_000L, "120"),
                tuple(MIDDLE_PRODUCT_ID, PRODUCT_A_NAME, PRODUCT_A_THUMBNAIL_URL, 25_000L, "120"));
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지는 남은 상품만 돌려주고 다음 페이지가 없다고 알린다")
    void lastPageOfNewestProducts() {
        givenVisibleChildAndThreeProducts();

        Page<RecommendedProductResult> result =
            service.list(MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, 1, 2);

        assertThat(result.getContent())
            .extracting(RecommendedProductResult::productId)
            .containsExactly(OLDEST_PRODUCT_ID);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("목록에 나온 상품의 판매자 복사본이 있으면 카드에 상호명이 들어간다")
    void fillBusinessNameFromSellerReplica() {
        givenVisibleChildAndThreeProducts();
        given(replicaSellerRepository.findAllById(any()))
            .willReturn(List.of(RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved())));

        Page<RecommendedProductResult> result =
            service.list(MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, FIRST_PAGE, 2);

        // 두 상품 모두 상품 A의 판매자가 팔고, 그 판매자 복사본의 상호명은 소소한옷장이다
        assertThat(result.getContent())
            .extracting(RecommendedProductResult::productId, RecommendedProductResult::businessName)
            .containsExactly(
                tuple(NEWEST_PRODUCT_ID, BUSINESS_NAME),
                tuple(MIDDLE_PRODUCT_ID, BUSINESS_NAME));
    }

    @Test
    @DisplayName("판매자 복사본이 없으면 상품을 목록에서 빼지 않고 상호명만 null이다")
    void leaveBusinessNameNullWithoutSellerReplica() {
        // 가장 최신 상품만 판매자 복사본이 없는 판매자가 판다
        givenVisibleChildAndProductsSoldBy(PRODUCT_A_SELLER_ID, OTHER_SELLER_ID, PRODUCT_A_SELLER_ID);
        given(replicaSellerRepository.findAllById(any()))
            .willReturn(List.of(RecommendReplicaSeller.create(RecommendReplicaSellerFixture.sellerApproved())));

        Page<RecommendedProductResult> result =
            service.list(MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, FIRST_PAGE, 2);

        assertThat(result.getContent())
            .extracting(RecommendedProductResult::productId, RecommendedProductResult::businessName)
            .containsExactly(
                tuple(NEWEST_PRODUCT_ID, null),
                tuple(MIDDLE_PRODUCT_ID, BUSINESS_NAME));
    }

    @Test
    @DisplayName("판매자 복사본은 이번 페이지 상품의 판매자 ID를 묶어 한 번만 읽는다")
    void readSellerReplicasOnceForPageSellers() {
        // 첫 페이지의 두 상품은 같은 판매자가 팔고, 다음 페이지로 밀린 가장 오래된 상품만 다른 판매자가 판다
        givenVisibleChildAndProductsSoldBy(OTHER_SELLER_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_SELLER_ID);

        service.list(MEMBER_1_ID, CHILD_1_ID, PRODUCT_A_CATEGORY_ID, FIRST_PAGE, 2);

        // 다음 페이지 상품의 판매자는 읽지 않고, 같은 판매자 ID는 한 번만 넘긴다
        verify(replicaSellerRepository, times(1)).findAllById(sellerIdsCaptor.capture());
        assertThat(sellerIdsCaptor.getValue()).containsExactlyInAnyOrder(PRODUCT_A_SELLER_ID);
    }

    private void givenVisibleChildAndThreeProducts() {
        givenVisibleChildAndProductsSoldBy(PRODUCT_A_SELLER_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_SELLER_ID);
    }

    // 상품 ID 순서와 등록 시각 순서가 다르게 해서, 상품 ID로 정렬하면 테스트가 실패하게 한다
    private void givenVisibleChildAndProductsSoldBy(Long oldestSellerId, Long newestSellerId, Long middleSellerId) {
        LocalDateTime oldest = PRODUCT_A_REGISTERED_AT;
        given(replicaChildRepository.findById(CHILD_1_ID))
            .willReturn(Optional.of(RecommendReplicaChild.create(RecommendFixture.childChange())));
        given(replicaProductRepository.findAllOnSale(PRODUCT_A_CATEGORY_ID)).willReturn(List.of(
            productReplicaRegisteredAt(OLDEST_PRODUCT_ID, oldestSellerId, oldest),
            productReplicaRegisteredAt(NEWEST_PRODUCT_ID, newestSellerId, oldest.plusDays(2)),
            productReplicaRegisteredAt(MIDDLE_PRODUCT_ID, middleSellerId, oldest.plusDays(1))));
    }

    static Stream<Arguments> unavailableChildren() {
        return Stream.of(
            Arguments.of(Named.of("자녀 복사본이 없다", Optional.empty()), MEMBER_1_ID),
            Arguments.of(
                Named.of("다른 회원의 자녀다", Optional.of(RecommendReplicaChild.create(RecommendFixture.childChange()))),
                OTHER_MEMBER_ID),
            Arguments.of(
                Named.of("삭제된 자녀다",
                    Optional.of(RecommendReplicaChild.create(RecommendFixture.childDeleted(CHILD_CHANGED_AT)))),
                MEMBER_1_ID));
    }
}
