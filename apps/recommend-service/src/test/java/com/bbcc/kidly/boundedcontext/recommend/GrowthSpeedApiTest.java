package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.global.security.TestMembers;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.FunctionalTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 자녀의 키 성장 속도를 상품 없이 조회하는 API를 HTTP 요청부터 응답까지 확인한다.
 */
@FunctionalTest
class GrowthSpeedApiTest {

    private static final String PATH = "/api/v1/recommendations/children/{childId}/growth-speed";

    private static final String SIZE_PATH = "/api/v1/recommendations/size?productId={productId}&childId={childId}";

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired
    private RestTestClient client;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private RecommendGrowthRecordRepository growthRecordRepository;

    // 자녀 1은 만 7세 남아이고, 만 7세 남의 표 값은 0.50cm/월이다
    @Test
    @DisplayName("GROW-02 성장 기록이 없는 자녀의 성장 속도는 표 값이고, 최근 속도는 null로 받는다")
    void findGrowthSpeedFromTableWithoutRecords() {
        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventBornOn(TODAY.minusYears(7).minusMonths(6)));

        find(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.cmPerMonth").isEqualTo(0.5)
            .jsonPath("$.recordCount").isEqualTo(0)
            .jsonPath("$.childSharePercent").isEqualTo(0)
            .jsonPath("$.tableCmPerMonth").isEqualTo(0.5)
            .jsonPath("$.tableSource").isEqualTo("한국아동패널")
            .jsonPath("$").value(Map.class,
                growthSpeed -> assertThat(growthSpeed).containsEntry("recentCmPerMonth", null));
    }

    @Test
    @DisplayName("성장 기록이 있는 자녀의 성장 속도는 같은 자녀의 사이즈 추천 응답에 담긴 성장 속도와 칸과 값이 모두 같다")
    void findSameGrowthSpeedAsSizeRecommendation() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventBornOn(TODAY.minusYears(7).minusMonths(6)));
        LocalDateTime now = TODAY.atTime(9, 0);
        saveGrowthRecord(1L, "111.0", now.minusDays(182));
        saveGrowthRecord(2L, "115.0", now.minusDays(1));
        AtomicReference<Map<?, ?>> fromSizeRecommendation = new AtomicReference<>();
        client.get().uri(SIZE_PATH, PRODUCT_A_ID, CHILD_1_ID)
            .headers(TestMembers.buyer(MEMBER_1_ID))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.growthSpeed").value(Map.class, fromSizeRecommendation::set);

        find(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.recordCount").isEqualTo(2)
            .jsonPath("$").value(Map.class,
                growthSpeed -> assertThat(growthSpeed).isEqualTo(fromSizeRecommendation.get()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invisibleChildren")
    @DisplayName("없는 자녀, 다른 회원의 자녀, 삭제된 자녀의 성장 속도를 조회하면 404 ProblemDetail을 받는다")
    void rejectInvisibleChild(Optional<ChildChangedEvent> childEvent) {
        childEvent.ifPresent(replicaChildService::applyChildChanged);

        find(MEMBER_1_ID, CHILD_1_ID)
            .expectStatus().isNotFound()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type").isEqualTo("about:blank")
            .jsonPath("$.title").isEqualTo("자녀를 찾을 수 없습니다")
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.detail").isEqualTo("자녀를 찾을 수 없습니다.")
            .jsonPath("$.instance").isEqualTo("/api/v1/recommendations/children/" + CHILD_1_ID + "/growth-speed");
    }

    static Stream<Arguments> invisibleChildren() {
        return Stream.of(
            Arguments.of(Named.of("없는 자녀", Optional.empty())),
            Arguments.of(Named.of("다른 회원의 자녀", Optional.of(childChanged(OTHER_MEMBER_ID, false)))),
            Arguments.of(Named.of("삭제된 자녀", Optional.of(childChanged(MEMBER_1_ID, true)))));
    }

    @Test
    @DisplayName("로그인하지 않고 성장 속도를 조회하면 401을 받는다")
    void rejectWithoutToken() {
        client.get().uri(PATH, CHILD_1_ID)
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private static ChildChangedEvent childChanged(Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT, new ChildChangedEvent.Data(
            CHILD_1_ID, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE,
            RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private void saveGrowthRecord(Long growthRecordId, String heightCm, LocalDateTime recordedAt) {
        growthRecordRepository.save(RecommendGrowthRecord.create(new RecommendGrowthRecord.RecordInfo(
            growthRecordId, CHILD_1_ID, new BigDecimal(heightCm), null, recordedAt)));
    }

    private RestTestClient.ResponseSpec find(Long memberId, Long childId) {
        return client.get().uri(PATH, childId)
            .headers(TestMembers.buyer(memberId))
            .exchange();
    }
}
