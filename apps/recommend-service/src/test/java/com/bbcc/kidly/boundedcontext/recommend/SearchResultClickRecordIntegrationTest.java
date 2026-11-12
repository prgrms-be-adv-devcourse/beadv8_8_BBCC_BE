package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CLICKED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.CLICK_EVENT_ID;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.KEYWORD;
import static com.bbcc.kidly.boundedcontext.recommend.ActivityRecordFixture.searchResultClickedEvent;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.search.event.SearchResultClickedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 활동 기록 서비스가 검색 결과 클릭을 실제 DB에 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendActivityKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class SearchResultClickRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

    @Autowired
    private ActivityRecordService activityRecordService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PREF-17 검색 결과 클릭 이벤트를 누른 상품 · 회원 · 누른 시각의 활동 기록으로 저장한다")
    void saveSearchResultClick() {
        activityRecordService.recordSearchResultClicked(searchResultClickedEvent(CLICK_EVENT_ID, CLICKED_AT, null));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(
                ActivityRecord::getType,
                ActivityRecord::getEventId,
                ActivityRecord::getMemberId,
                ActivityRecord::getChildProfileId,
                ActivityRecord::getProductId,
                ActivityRecord::getOccurredAt)
            .containsExactly(ActivityType.SEARCH_RESULT_CLICKED, CLICK_EVENT_ID, MEMBER_1_ID, null, PRODUCT_A_ID,
                CLICKED_AT);
    }

    @Test
    @DisplayName("PREF-17 같은 검색 결과 클릭 이벤트를 다시 받아도 예외 없이 활동 기록은 한 줄이다")
    void keepOneRecordForSameClickEvent() {
        var event = searchResultClickedEvent(CLICK_EVENT_ID, CLICKED_AT, null);
        activityRecordService.recordSearchResultClicked(event);

        assertThatCode(() -> activityRecordService.recordSearchResultClicked(event)).doesNotThrowAnyException();

        assertThat(activityRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PREF-17 같은 회원 · 상품을 같은 날 다시 누르면 처음 클릭만 기록으로 남긴다")
    void keepFirstClickOfSameDay() {
        activityRecordService.recordSearchResultClicked(
            searchResultClickedEvent(UUID.randomUUID(), CLICKED_AT.withHour(0).withMinute(0), null));

        activityRecordService.recordSearchResultClicked(
            searchResultClickedEvent(UUID.randomUUID(), CLICKED_AT.withHour(23).withMinute(59), null));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getOccurredAt).isEqualTo(CLICKED_AT.withHour(0).withMinute(0));
    }

    @ParameterizedTest(name = "두 번째 클릭: {0}일 뒤, 상품 ID +{1}")
    @CsvSource({
        "1, 0",
        "0, 1",
    })
    @DisplayName("PREF-17 같은 회원이라도 날짜나 상품이 다르면 클릭을 각각 기록한다")
    void keepClicksOfOtherDayOrProduct(long daysLater, long productIdOffset) {
        activityRecordService.recordSearchResultClicked(
            searchResultClickedEvent(UUID.randomUUID(), CLICKED_AT.withHour(23).withMinute(59), null));

        activityRecordService.recordSearchResultClicked(new SearchResultClickedEvent(UUID.randomUUID(),
            CLICKED_AT.withHour(0).withMinute(0).plusDays(daysLater),
            new SearchResultClickedEvent.Data(MEMBER_1_ID, KEYWORD, PRODUCT_A_ID + productIdOffset, 1, "NEWEST",
                null)));

        assertThat(activityRecordRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("PREF-17 누른 회원의 자녀를 골랐으면 그 자녀의 클릭으로 기록한다")
    void keepOwnChild() {
        PreferenceTestData.insertReplicaChild(jdbc, CHILD_1_ID, null);

        activityRecordService.recordSearchResultClicked(searchResultClickedEvent(CLICK_EVENT_ID, CLICKED_AT,
            CHILD_1_ID));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getChildProfileId).isEqualTo(CHILD_1_ID);
    }

    // 자녀 복사본의 주인은 회원 1이다
    @ParameterizedTest(name = "자녀 복사본 {0}, 누른 회원 {1}")
    @CsvSource({
        "없음, 1",
        "지움, 1",
        "있음, 2",
    })
    @DisplayName("PREF-17 모르는 자녀 · 지운 자녀 · 다른 회원의 자녀를 골랐다고 오면 실패시키지 않고 자녀 없이 기록한다")
    void dropChildNotVisibleToMember(String replica, Long memberId) {
        if (!"없음".equals(replica)) {
            PreferenceTestData.insertReplicaChild(jdbc, CHILD_1_ID, "지움".equals(replica) ? CLICKED_AT : null);
        }

        activityRecordService.recordSearchResultClicked(new SearchResultClickedEvent(CLICK_EVENT_ID, CLICKED_AT,
            new SearchResultClickedEvent.Data(memberId, KEYWORD, PRODUCT_A_ID, 1, "NEWEST", CHILD_1_ID)));

        assertThat(activityRecordRepository.findAll()).singleElement()
            .extracting(ActivityRecord::getMemberId, ActivityRecord::getChildProfileId)
            .containsExactly(memberId, null);
    }
}
