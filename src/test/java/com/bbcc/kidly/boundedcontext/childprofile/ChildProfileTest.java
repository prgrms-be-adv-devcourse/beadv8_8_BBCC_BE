package com.bbcc.kidly.boundedcontext.childprofile;

import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.HEIGHT;
import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.NOW;
import static com.bbcc.kidly.boundedcontext.childprofile.ChildProfileFixture.WEIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecord;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChildProfileTest {

    private static final Long MEMBER_ID = 1L;

    @Test
    @DisplayName("등록하면 입력한 키 · 체형으로 성장 이력 1건이 함께 생긴다")
    void registerCreatesGrowthRecord() {
        var child = ChildProfileFixture.child(MEMBER_ID);

        assertThat(child.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(child.isDeleted()).isFalse();
        assertThat(child.getGrowthRecords()).singleElement().satisfies(record -> {
            assertThat(record.getHeightCm()).isEqualByComparingTo(HEIGHT);
            assertThat(record.getBodyType()).isEqualTo(BodyType.NORMAL);
            assertThat(record.getRecordedAt()).isEqualTo(NOW);
        });
        assertThat(child.growthRecordCount()).isEqualTo(1);
        assertThat(child.lastRecordedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("키가 바뀌면 성장 이력이 1건 추가된다")
    void updateHeightAddsRecord() {
        var child = ChildProfileFixture.child(MEMBER_ID);
        var later = NOW.plusDays(30);

        child.update("첫째", BIRTH_DATE, Gender.MALE, new BigDecimal("117.5"), WEIGHT,
            BodyType.NORMAL, PreferredFit.LOOSE, later);

        assertThat(child.getHeightCm()).isEqualByComparingTo("117.5");
        assertThat(child.getGrowthRecords()).hasSize(2);
        assertThat(child.lastRecordedAt()).isEqualTo(later);
        assertThat(child.getGrowthRecords().get(1).getHeightCm()).isEqualByComparingTo("117.5");
    }

    @Test
    @DisplayName("체형이 비어 있다가 생기거나 비워져도 성장 이력이 추가된다")
    void updateBodyTypeNullToValueAddsRecord() {
        var child = ChildProfile.register(
            MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE, HEIGHT, WEIGHT, null, null, NOW);

        child.update("첫째", BIRTH_DATE, Gender.MALE, HEIGHT, WEIGHT, BodyType.SLIM, null, NOW.plusDays(1));
        child.update("첫째", BIRTH_DATE, Gender.MALE, HEIGHT, WEIGHT, null, null, NOW.plusDays(2));

        assertThat(child.getGrowthRecords()).extracting(GrowthRecord::getBodyType)
            .containsExactly(null, BodyType.SLIM, null);
    }

    @Test
    @DisplayName("이름 · 몸무게 · 선호 핏만 바뀌면 성장 이력을 쌓지 않는다")
    void updateWithoutHeightOrBodyTypeChangeKeepsRecords() {
        var child = ChildProfileFixture.child(MEMBER_ID);

        child.update("둘째", BIRTH_DATE, Gender.MALE, HEIGHT, new BigDecimal("21.0"),
            BodyType.NORMAL, PreferredFit.SLIM, NOW.plusDays(1));

        assertThat(child.getName()).isEqualTo("둘째");
        assertThat(child.getWeightKg()).isEqualByComparingTo("21.0");
        assertThat(child.getPreferredFit()).isEqualTo(PreferredFit.SLIM);
        assertThat(child.getGrowthRecords()).hasSize(1);
    }

    // 115.0과 115는 같은 키다
    @Test
    @DisplayName("같은 값으로 수정하면 성장 이력을 쌓지 않는다")
    void updateWithSameValuesKeepsRecords() {
        var child = ChildProfileFixture.child(MEMBER_ID);

        child.update("첫째", BIRTH_DATE, Gender.MALE, new BigDecimal("115"), WEIGHT,
            BodyType.NORMAL, PreferredFit.LOOSE, NOW.plusDays(1));

        assertThat(child.getGrowthRecords()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"39.9", "200.1", "115.25", "115.00"})
    @DisplayName("키가 40.0~200.0을 벗어나거나 소수 둘째 자리가 있으면 등록할 수 없다")
    void registerRejectsInvalidHeight(String height) {
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE,
            new BigDecimal(height), WEIGHT, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.9", "150.1", "20.05", "20.00"})
    @DisplayName("몸무게가 2.0~150.0을 벗어나거나 소수 둘째 자리가 있으면 등록할 수 없다")
    void registerRejectsInvalidWeight(String weight) {
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE,
            HEIGHT, new BigDecimal(weight), null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생년월일이 미래면 등록할 수 없다")
    void registerRejectsFutureBirthDate() {
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", NOW.toLocalDate().plusDays(1),
            Gender.MALE, HEIGHT, WEIGHT, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이름이 비어 있으면 등록할 수 없다")
    void registerRejectsBlankName() {
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, " ", BIRTH_DATE, Gender.MALE,
            HEIGHT, WEIGHT, null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("필수 값이 null이면 등록할 수 없다")
    void registerRejectsNull() {
        assertThatThrownBy(() -> ChildProfile.register(null, "첫째", BIRTH_DATE, Gender.MALE,
            HEIGHT, WEIGHT, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", null, Gender.MALE,
            HEIGHT, WEIGHT, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, null,
            HEIGHT, WEIGHT, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE,
            null, WEIGHT, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE,
            HEIGHT, null, null, null, NOW))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChildProfile.register(MEMBER_ID, "첫째", BIRTH_DATE, Gender.MALE,
            HEIGHT, WEIGHT, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("수정도 같은 검증을 한다")
    void updateValidates() {
        var child = ChildProfileFixture.child(MEMBER_ID);

        assertThatThrownBy(() -> child.update("첫째", BIRTH_DATE, Gender.MALE, new BigDecimal("300.0"), WEIGHT,
            null, null, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(child.getHeightCm()).isEqualByComparingTo(HEIGHT);
        assertThat(child.getGrowthRecords()).hasSize(1);
    }

    @Test
    @DisplayName("삭제하면 삭제 시각이 남고 값은 그대로다")
    void delete() {
        var child = ChildProfileFixture.child(MEMBER_ID);

        child.delete(NOW.plusDays(1));

        assertThat(child.isDeleted()).isTrue();
        assertThat(child.getDeletedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(child.getName()).isEqualTo("첫째");
    }
}
