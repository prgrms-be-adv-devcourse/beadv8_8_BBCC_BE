package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendedProductServiceTest {

    private static final int FIRST_PAGE = 0;

    private static final int PAGE_SIZE = 20;

    @Mock
    private RecommendReplicaProductRepository replicaProductRepository;

    @Mock
    private RecommendReplicaChildRepository replicaChildRepository;

    @Mock
    private RecommendReplicaSellerRepository replicaSellerRepository;

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
