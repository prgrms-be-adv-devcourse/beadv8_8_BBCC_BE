package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReplicaChild;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
}
