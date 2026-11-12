package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.BASE_TIME;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendedProductFixture.recommendedProductRegisteredAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.RecommendAiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiCallQuota;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiAnswer;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewPicker;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.HomePreviewService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewResult;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidates;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * 홈 맛보기 서비스가 AI 공통 서비스를 거쳐 AI 답을 고르는 흐름을 가짜 AI와 메모리 저장소로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class HomePreviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private static final Long MEMBER_ID = 1L;

    private static final Long CHILD_ID = 11L;

    private static final HomePreviewAiRequest.Child CHILD = new HomePreviewAiRequest.Child(
        48, new BigDecimal("105.5"), new BigDecimal("17.2"), BodyType.NORMAL, PreferredFit.REGULAR);

    @Mock
    private HomePreviewCandidateReader candidateReader;

    @Mock
    private RecommendReplicaSellerRepository replicaSellerRepository;

    private final MemoryAiResponseStore store = new MemoryAiResponseStore();

    private final CountingAiCallQuota quota = new CountingAiCallQuota();

    private AiResponseService aiResponseService;

    @BeforeEach
    void setUp() {
        aiResponseService = new AiResponseService(store, quota, JsonMapper.builder().build(), CLOCK,
            2_000, Map.of(AiResponseType.HOME_PREVIEW, Duration.ofSeconds(1)), Duration.ofSeconds(10),
            Duration.ofHours(24), new RecommendAiMetrics(new SimpleMeterRegistry(), ObservationRegistry.NOOP));
    }

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final RecommendAiMetrics metrics = new RecommendAiMetrics(meterRegistry, ObservationRegistry.NOOP);

    @Test
    @DisplayName("HOME-03 AI가 고른 상품을 AI 답 순서와 이유 그대로 돌려준다")
    void aiPicksWithReasons() {
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        RecordingPicker picker = new RecordingPicker(
            answer(5L, "이유 5이에요.", 2L, "이유 2이에요.", 6L, "이유 6이에요.", 1L, "이유 1이에요."));

        HomePreviewResult result = service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.items())
            .extracting(item -> item.product().productId(), HomePreviewResult.Item::reason)
            .containsExactly(tuple(5L, "이유 5이에요."), tuple(2L, "이유 2이에요."), tuple(6L, "이유 6이에요."),
                tuple(1L, "이유 1이에요."));
    }

    @Test
    @DisplayName("HOME-02 chat 모델이 없으면 AI를 부르지 않고 하루 호출 횟수도 세지 않은 채 규칙 결과를 돌려준다")
    void ruleResultWithoutPicker() {
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);

        HomePreviewResult result = service(null).preview(MEMBER_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.items()).extracting(item -> item.product().productId()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(quota.acquired).isZero();
    }

    @Test
    @DisplayName("HOME-02 후보가 없으면 AI를 부르지 않고 하루 호출 횟수도 세지 않는다")
    void skipAiWithoutCandidates() {
        givenCandidates();
        RecordingPicker picker = new RecordingPicker(answer(1L, "이유 1이에요."));

        HomePreviewResult result = service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.items()).isEmpty();
        assertThat(picker.requests).isEmpty();
        assertThat(quota.acquired).isZero();
    }

    @Test
    @DisplayName("HOME-02 AI 호출이 실패하면 규칙 결과를 돌려준다")
    void ruleResultWhenAiFails() {
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        HomePreviewPicker failingPicker = request -> {
            throw new IllegalStateException("AI 호출 실패");
        };

        HomePreviewResult result = service(failingPicker).preview(MEMBER_ID, CHILD_ID);

        assertThat(result.source()).isEqualTo("RULE");
        assertThat(result.items()).extracting(item -> item.product().productId()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("자녀 값과 후보 구성이 같으면 후보 순서만 바뀌어도 저장한 AI 답을 다시 쓰고 AI를 다시 부르지 않는다")
    void reuseStoredAnswerWhenOnlyOrderChanges() {
        RecordingPicker picker = new RecordingPicker(answer(5L, "이유 5이에요."));
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        service(picker).preview(MEMBER_ID, CHILD_ID);
        // AI 답은 다른 스레드에서 저장하므로 저장될 때까지 기다린다
        store.waitUntilSaved();
        givenCandidates(6L, 5L, 4L, 3L, 2L, 1L);

        HomePreviewResult result = service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(picker.requests).hasSize(1);
        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.items().getFirst().reason()).isEqualTo("이유 5이에요.");
    }

    @Test
    @DisplayName("HOME-07 근거를 넣기 전 프롬프트(버전 1)로 저장한 답은 자녀 값과 후보가 같아도 다시 쓰지 않고 AI를 다시 부른다")
    void callAiAgainForAnswerOfOldPromptVersion() {
        store.save(AiResponseType.HOME_PREVIEW, AiResponseKey.of(new AiResponseKey.Input(null, CHILD_ID,
                CHILD.ageMonths(), CHILD.heightCm(), CHILD.weightKg(), CHILD.bodyType(), CHILD.preferredFit(),
                List.of(1L, 2L, 3L, 4L, 5L, 6L), null, 1)),
            "{\"picks\":[{\"productId\":5,\"reason\":\"추천 사이즈가 110인 상품이에요.\"}]}", Duration.ofHours(24));
        RecordingPicker picker = new RecordingPicker(answer(5L, "포근한 겨울 상의로 추천."));
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);

        HomePreviewResult result = service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(picker.requests).hasSize(1);
        assertThat(result.items().getFirst().reason()).isEqualTo("포근한 겨울 상의로 추천.");
    }

    @Test
    @DisplayName("후보 구성이 바뀌면 저장한 답을 쓰지 않고 AI를 다시 부른다")
    void callAiAgainWhenCandidatesChange() {
        RecordingPicker picker = new RecordingPicker(answer(5L, "이유 5이에요."));
        givenCandidates(1L, 2L, 3L, 4L, 5L, 6L);
        service(picker).preview(MEMBER_ID, CHILD_ID);
        givenCandidates(1L, 2L, 3L, 4L, 5L, 7L);

        service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(picker.requests).hasSize(2);
    }

    @ParameterizedTest
    @MethodSource("changedChildren")
    @DisplayName("자녀의 개월 수 · 키 · 몸무게 · 체형 · 선호 핏 중 하나가 바뀌면 저장한 답을 쓰지 않고 AI를 다시 부른다")
    void callAiAgainWhenChildChanges(HomePreviewAiRequest.Child changedChild) {
        RecordingPicker picker = new RecordingPicker(answer(5L, "이유 5이에요."));
        givenCandidates(CHILD, 1L, 2L, 3L, 4L, 5L, 6L);
        service(picker).preview(MEMBER_ID, CHILD_ID);
        givenCandidates(changedChild, 1L, 2L, 3L, 4L, 5L, 6L);

        service(picker).preview(MEMBER_ID, CHILD_ID);

        assertThat(picker.requests).hasSize(2);
    }

    static List<HomePreviewAiRequest.Child> changedChildren() {
        return List.of(
            new HomePreviewAiRequest.Child(
                49, CHILD.heightCm(), CHILD.weightKg(), CHILD.bodyType(), CHILD.preferredFit()),
            new HomePreviewAiRequest.Child(
                CHILD.ageMonths(), new BigDecimal("106.0"), CHILD.weightKg(), CHILD.bodyType(), CHILD.preferredFit()),
            new HomePreviewAiRequest.Child(
                CHILD.ageMonths(), CHILD.heightCm(), new BigDecimal("18.0"), CHILD.bodyType(), CHILD.preferredFit()),
            new HomePreviewAiRequest.Child(
                CHILD.ageMonths(), CHILD.heightCm(), CHILD.weightKg(), BodyType.SLIM, CHILD.preferredFit()),
            new HomePreviewAiRequest.Child(
                CHILD.ageMonths(), CHILD.heightCm(), CHILD.weightKg(), CHILD.bodyType(), PreferredFit.SLIM));
    }

    private HomePreviewService service(HomePreviewPicker picker) {
        return new HomePreviewService(candidateReader, replicaSellerRepository, aiResponseService,
            Optional.ofNullable(picker), metrics);
    }

    // 상품 ID 순서가 선호 점수 순서인 후보를 읽게 한다
    private void givenCandidates(Long... productIds) {
        givenCandidates(CHILD, productIds);
    }

    // 자녀 값을 정해 상품 ID 순서가 선호 점수 순서인 후보를 읽게 한다
    private void givenCandidates(HomePreviewAiRequest.Child child, Long... productIds) {
        List<RecommendedProduct> products = new ArrayList<>();
        List<HomePreviewAiRequest.Candidate> aiCandidates = new ArrayList<>();
        for (Long productId : productIds) {
            RecommendedProduct product = recommendedProductRegisteredAt(productId, BASE_TIME);
            products.add(product);
            aiCandidates.add(new HomePreviewAiRequest.Candidate(productId, product.name(), product.brand(),
                List.of("의류", "상의"), product.minPrice(), product.recommendedSize()));
        }
        given(candidateReader.read(MEMBER_ID, CHILD_ID)).willReturn(
            new HomePreviewCandidates(CHILD_ID, products, new HomePreviewAiRequest(child, aiCandidates)));
    }

    // 상품 ID와 이유를 번갈아 받아 AI 답을 만든다
    private static HomePreviewAiAnswer answer(Object... idAndReasons) {
        List<HomePreviewAiAnswer.Pick> picks = new ArrayList<>();
        for (int i = 0; i < idAndReasons.length; i += 2) {
            picks.add(new HomePreviewAiAnswer.Pick((Long) idAndReasons[i], (String) idAndReasons[i + 1]));
        }
        return new HomePreviewAiAnswer(picks);
    }

    /**
     * 받은 요청을 남기고 정해 둔 답을 돌려주는 가짜 AI다.
     */
    private static final class RecordingPicker implements HomePreviewPicker {

        private final HomePreviewAiAnswer answer;

        private final List<HomePreviewAiRequest> requests = new ArrayList<>();

        private RecordingPicker(HomePreviewAiAnswer answer) {
            this.answer = answer;
        }

        @Override
        public HomePreviewAiAnswer pick(HomePreviewAiRequest request) {
            requests.add(request);
            return answer;
        }
    }

    /**
     * 같은 종류 · 키의 가장 최근 응답만 기억하는 메모리 저장소다. 보관 기간은 따지지 않는다.
     */
    private static final class MemoryAiResponseStore implements AiResponseStore {

        private final Map<String, String> responses = new ConcurrentHashMap<>();

        @Override
        public Optional<String> find(AiResponseType type, AiResponseKey key) {
            return Optional.ofNullable(responses.get(type + ":" + key.value()));
        }

        @Override
        public void save(AiResponseType type, AiResponseKey key, String responseJson, Duration timeToLive) {
            responses.put(type + ":" + key.value(), responseJson);
        }

        void waitUntilSaved() {
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (responses.isEmpty()) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("1초 안에 AI 답이 저장되지 않았습니다");
                }
                Thread.onSpinWait();
            }
        }
    }

    /**
     * 호출 횟수를 세기만 하고 늘 허용하는 가짜 하루 상한이다.
     */
    private static final class CountingAiCallQuota implements AiCallQuota {

        private int acquired;

        @Override
        public boolean tryAcquire(LocalDate date, int dailyLimit) {
            acquired++;
            return true;
        }
    }
}
