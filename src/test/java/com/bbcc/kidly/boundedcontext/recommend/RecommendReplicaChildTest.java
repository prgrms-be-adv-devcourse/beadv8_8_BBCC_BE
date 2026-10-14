package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecommendReplicaChildTest {

    private static final BigDecimal HEIGHT = new BigDecimal("120.0");

    private static final BigDecimal WEIGHT = new BigDecimal("22.0");

    private static final LocalDateTime LATER = CHILD_CHANGED_AT.plusMinutes(1);

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstChanges")
    @DisplayName("처음 받은 자녀 정보로 복사본을 만든다")
    void createWithFirstChange(RecommendReplicaChild.ChangeInfo info) {
        RecommendReplicaChild replica = RecommendReplicaChild.create(info);

        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getChildProfileId,
                RecommendReplicaChild::getMemberId,
                RecommendReplicaChild::getHeightCm,
                RecommendReplicaChild::getWeightKg,
                RecommendReplicaChild::getGender,
                RecommendReplicaChild::getBirthDate,
                RecommendReplicaChild::getBodyType,
                RecommendReplicaChild::getPreferredFit,
                RecommendReplicaChild::getEventOccurredAt,
                RecommendReplicaChild::isDeleted)
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

    static Stream<Named<RecommendReplicaChild.ChangeInfo>> firstChanges() {
        return Stream.of(
            Named.of("체형 보통 · 선호 핏 여유", RecommendFixture.childChange()),
            Named.of("체형 · 선호 핏 입력 안 함", RecommendFixture.childChange(null, null)));
    }

    @Test
    @DisplayName("INV-R01 더 늦게 일어난 이벤트의 값으로 덮어쓴다")
    void applyLaterChange() {
        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childChange());
        LocalDateTime later = CHILD_CHANGED_AT.plusMinutes(1);

        replica.applyIfNewer(new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("120.0"),
            new BigDecimal("22.0"), Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.CHUNKY, null, false, later));

        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getHeightCm,
                RecommendReplicaChild::getWeightKg,
                RecommendReplicaChild::getBodyType,
                RecommendReplicaChild::getPreferredFit,
                RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(new BigDecimal("120.0"), new BigDecimal("22.0"), BodyType.CHUNKY, null, later);
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트는 무시한다")
    void ignoreChangeNotLater(long minutesFromStored) {
        RecommendReplicaChild replica = RecommendReplicaChild.create(
            RecommendFixture.childChangeAt(new BigDecimal("115.0"), CHILD_CHANGED_AT));

        replica.applyIfNewer(
            RecommendFixture.childChangeAt(new BigDecimal("120.0"), CHILD_CHANGED_AT.plusMinutes(minutesFromStored)));

        assertThat(replica)
            .extracting(RecommendReplicaChild::getHeightCm, RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(new BigDecimal("115.0"), CHILD_CHANGED_AT);
    }

    @Test
    @DisplayName("INV-R02 삭제 이벤트를 받으면 행을 지우지 않고 그 이벤트가 일어난 시각을 삭제 시각으로 남긴다")
    void markDeletedOnDeleteChange() {
        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childChange());
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);

        replica.applyIfNewer(RecommendFixture.childDeleted(deletedAt));

        assertThat(replica)
            .extracting(
                RecommendReplicaChild::isDeleted,
                RecommendReplicaChild::getDeletedAt,
                RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(true, deletedAt, deletedAt);
    }

    @Test
    @DisplayName("INV-R02 처음 받은 이벤트가 삭제여도 삭제된 복사본으로 만든다")
    void createDeletedWhenFirstChangeIsDelete() {
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);

        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childDeleted(deletedAt));

        assertThat(replica)
            .extracting(RecommendReplicaChild::isDeleted, RecommendReplicaChild::getDeletedAt)
            .containsExactly(true, deletedAt);
    }

    @Test
    @DisplayName("INV-R02 삭제된 뒤 늦게 도착한 이전 수정 이벤트는 무시한다")
    void ignoreEarlierChangeAfterDelete() {
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);
        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childDeleted(deletedAt));

        replica.applyIfNewer(RecommendFixture.childChangeAt(new BigDecimal("120.0"), CHILD_CHANGED_AT));

        assertThat(replica)
            .extracting(
                RecommendReplicaChild::isDeleted,
                RecommendReplicaChild::getHeightCm,
                RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(true, new BigDecimal("115.0"), deletedAt);
    }

    @Test
    @DisplayName("INV-R02 삭제되지 않은 자기 자녀는 볼 수 있다")
    void visibleToOwner() {
        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childChange());

        assertThat(replica.isVisibleTo(MEMBER_1_ID)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invisibleChildren")
    @DisplayName("INV-R02 다른 회원의 자녀와 삭제된 자녀는 볼 수 없다")
    void invisibleToOthersOrWhenDeleted(RecommendReplicaChild replica, Long memberId) {
        assertThat(replica.isVisibleTo(memberId)).isFalse();
    }

    static Stream<Arguments> invisibleChildren() {
        return Stream.of(
            Arguments.of(
                Named.of("다른 회원의 자녀", RecommendReplicaChild.create(RecommendFixture.childChange())),
                OTHER_MEMBER_ID),
            Arguments.of(
                Named.of("삭제된 자기 자녀", RecommendReplicaChild.create(RecommendFixture.childDeleted(CHILD_CHANGED_AT))),
                MEMBER_1_ID));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("creationsWithNull")
    @DisplayName("자녀 정보에 필수 값이 없으면 복사본을 만들지 않는다")
    void rejectCreationWithNull(ThrowingCallable creation) {
        assertThatThrownBy(creation)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
    }

    static Stream<Named<ThrowingCallable>> creationsWithNull() {
        return Stream.of(
            Named.of("자녀 정보", () -> RecommendReplicaChild.create(null)),
            Named.of("자녀 ID", () -> RecommendReplicaChild.create(
                change(null, MEMBER_1_ID, HEIGHT, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, LATER))),
            Named.of("회원 ID", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, null, HEIGHT, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, LATER))),
            Named.of("키", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, MEMBER_1_ID, null, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, LATER))),
            Named.of("몸무게", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, null, Gender.MALE, CHILD_1_BIRTH_DATE, LATER))),
            Named.of("성별", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, null, CHILD_1_BIRTH_DATE, LATER))),
            Named.of("생년월일", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, Gender.MALE, null, LATER))),
            Named.of("이벤트 발생 시각", () -> RecommendReplicaChild.create(
                change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, null))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesWithNull")
    @DisplayName("자녀 정보에 필수 값이 없으면 복사본을 덮어쓰지 않고 이전 값을 그대로 둔다")
    void rejectChangeWithNull(RecommendReplicaChild.ChangeInfo change) {
        RecommendReplicaChild replica = RecommendReplicaChild.create(RecommendFixture.childChange());

        assertThatThrownBy(() -> replica.applyIfNewer(change))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getMemberId,
                RecommendReplicaChild::getHeightCm,
                RecommendReplicaChild::getWeightKg,
                RecommendReplicaChild::getGender,
                RecommendReplicaChild::getBirthDate,
                RecommendReplicaChild::getEventOccurredAt)
            .containsExactly(MEMBER_1_ID, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE,
                CHILD_1_BIRTH_DATE, CHILD_CHANGED_AT);
    }

    static Stream<Named<RecommendReplicaChild.ChangeInfo>> changesWithNull() {
        return Stream.of(
            Named.of("자녀 정보", null),
            Named.of("회원 ID", change(CHILD_1_ID, null, HEIGHT, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, LATER)),
            Named.of("키", change(CHILD_1_ID, MEMBER_1_ID, null, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, LATER)),
            Named.of("몸무게", change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, null, Gender.MALE, CHILD_1_BIRTH_DATE, LATER)),
            Named.of("성별", change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, null, CHILD_1_BIRTH_DATE, LATER)),
            Named.of("생년월일", change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, Gender.MALE, null, LATER)),
            Named.of("이벤트 발생 시각",
                change(CHILD_1_ID, MEMBER_1_ID, HEIGHT, WEIGHT, Gender.MALE, CHILD_1_BIRTH_DATE, null)));
    }

    private static RecommendReplicaChild.ChangeInfo change(Long childProfileId, Long memberId, BigDecimal heightCm,
        BigDecimal weightKg, Gender gender, LocalDate birthDate, LocalDateTime occurredAt) {
        return new RecommendReplicaChild.ChangeInfo(childProfileId, memberId, heightCm, weightKg, gender, birthDate,
            BodyType.NORMAL, PreferredFit.LOOSE, false, occurredAt);
    }
}
