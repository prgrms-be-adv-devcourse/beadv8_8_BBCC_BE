package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaChild;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReplicaChildTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstChanges")
    @DisplayName("처음 받은 자녀 정보로 복사본을 만든다")
    void createWithFirstChange(ReplicaChild.ChangeInfo info) {
        ReplicaChild replica = ReplicaChild.create(info);

        assertThat(replica)
            .extracting(
                ReplicaChild::getChildProfileId,
                ReplicaChild::getMemberId,
                ReplicaChild::getHeightCm,
                ReplicaChild::getWeightKg,
                ReplicaChild::getGender,
                ReplicaChild::getBirthDate,
                ReplicaChild::getBodyType,
                ReplicaChild::getPreferredFit,
                ReplicaChild::getEventOccurredAt,
                ReplicaChild::isDeleted)
            .containsExactly(
                info.childProfileId(),
                info.memberId(),
                info.heightCm(),
                info.weightKg(),
                info.gender(),
                info.birthDate(),
                info.bodyType(),
                info.preferredFit(),
                info.occurredAt(),
                false);
    }

    static Stream<Named<ReplicaChild.ChangeInfo>> firstChanges() {
        return Stream.of(
            Named.of("체형 보통 · 선호 핏 여유", RecommendFixture.childChange()),
            Named.of("체형 · 선호 핏 입력 안 함", RecommendFixture.childChange((BodyType) null, (PreferredFit) null)));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 이벤트의 값으로 덮어쓴다")
    void applyLaterChange() {
        ReplicaChild replica = ReplicaChild.create(RecommendFixture.childChange());
        LocalDateTime later = CHILD_CHANGED_AT.plusMinutes(1);

        replica.applyIfNewer(new ReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("120.0"),
            new BigDecimal("22.0"), Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.CHUNKY, null, false, later));

        assertThat(replica)
            .extracting(
                ReplicaChild::getHeightCm,
                ReplicaChild::getWeightKg,
                ReplicaChild::getBodyType,
                ReplicaChild::getPreferredFit,
                ReplicaChild::getEventOccurredAt)
            .containsExactly(new BigDecimal("120.0"), new BigDecimal("22.0"), BodyType.CHUNKY, null, later);
    }

    @ParameterizedTest(name = "저장된 시각 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트는 무시한다")
    void ignoreChangeNotLater(long minutesFromStored) {
        ReplicaChild replica = ReplicaChild.create(RecommendFixture.childChange(new BigDecimal("115.0"), CHILD_CHANGED_AT));

        replica.applyIfNewer(
            RecommendFixture.childChange(new BigDecimal("120.0"), CHILD_CHANGED_AT.plusMinutes(minutesFromStored)));

        assertThat(replica)
            .extracting(ReplicaChild::getHeightCm, ReplicaChild::getEventOccurredAt)
            .containsExactly(new BigDecimal("115.0"), CHILD_CHANGED_AT);
    }

    @Test
    @DisplayName("INV-R02 삭제 이벤트를 받으면 행을 지우지 않고 그 이벤트가 일어난 시각을 삭제 시각으로 남긴다")
    void markDeletedOnDeleteChange() {
        ReplicaChild replica = ReplicaChild.create(RecommendFixture.childChange());
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);

        replica.applyIfNewer(RecommendFixture.childDeleted(deletedAt));

        assertThat(replica)
            .extracting(ReplicaChild::isDeleted, ReplicaChild::getDeletedAt, ReplicaChild::getEventOccurredAt)
            .containsExactly(true, deletedAt, deletedAt);
    }
}
