package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityRecordServiceSearchTest {

    private static final Long OTHER_MEMBER_ID = MEMBER_1_ID + 1;

    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @Mock
    private RecommendReplicaChildRepository replicaChildRepository;

    @InjectMocks
    private ActivityRecordService service;

    @Test
    @DisplayName("검색한 회원의 자녀를 골랐으면 검색 기록에 그 자녀를 남긴다")
    void keepsOwnChild() {
        givenReplicaChild(RecommendReplicaChild.create(RecommendFixture.childChange()));

        service.recordSearch(ActivityRecordFixture.searchPerformedEventWithChild(CHILD_1_ID));

        assertThat(savedRecord().getChildProfileId()).isEqualTo(CHILD_1_ID);
    }

    @Test
    @DisplayName("다른 회원의 자녀를 골랐다고 오면 자녀 없이 검색을 기록한다")
    void dropsOtherMembersChild() {
        RecommendReplicaChild.ChangeInfo own = RecommendFixture.childChange();
        givenReplicaChild(RecommendReplicaChild.create(new RecommendReplicaChild.ChangeInfo(CHILD_1_ID,
            OTHER_MEMBER_ID, own.heightCm(), own.weightKg(), own.gender(), own.birthDate(), own.bodyType(),
            own.preferredFit(), false, own.occurredAt())));

        service.recordSearch(ActivityRecordFixture.searchPerformedEventWithChild(CHILD_1_ID));

        assertThat(savedRecord().getChildProfileId()).isNull();
        assertThat(savedRecord().getKeyword()).isEqualTo(ActivityRecordFixture.KEYWORD);
    }

    @Test
    @DisplayName("모르는 자녀를 골랐다고 오면 자녀 없이 검색을 기록한다")
    void dropsUnknownChild() {
        lenient().when(replicaChildRepository.findById(CHILD_1_ID)).thenReturn(Optional.empty());

        service.recordSearch(ActivityRecordFixture.searchPerformedEventWithChild(CHILD_1_ID));

        assertThat(savedRecord().getChildProfileId()).isNull();
    }

    @Test
    @DisplayName("지운 자녀를 골랐다고 오면 자녀 없이 검색을 기록한다")
    void dropsDeletedChild() {
        RecommendReplicaChild child = RecommendReplicaChild.create(RecommendFixture.childChange());
        child.applyIfNewer(RecommendFixture.childDeleted(RecommendFixture.CHILD_CHANGED_AT.plusDays(1)));
        givenReplicaChild(child);

        service.recordSearch(ActivityRecordFixture.searchPerformedEventWithChild(CHILD_1_ID));

        assertThat(savedRecord().getChildProfileId()).isNull();
    }

    @Test
    @DisplayName("자녀를 고르지 않았으면 자녀 없이 검색을 기록한다")
    void withoutChild() {
        service.recordSearch(ActivityRecordFixture.searchPerformedEvent());

        assertThat(savedRecord().getChildProfileId()).isNull();
        assertThat(savedRecord().getMemberId()).isEqualTo(MEMBER_1_ID);
    }

    private void givenReplicaChild(RecommendReplicaChild child) {
        lenient().when(replicaChildRepository.findById(CHILD_1_ID)).thenReturn(Optional.of(child));
    }

    private ActivityRecord savedRecord() {
        ArgumentCaptor<ActivityRecord> captor = ArgumentCaptor.forClass(ActivityRecord.class);
        verify(activityRecordRepository).save(captor.capture());
        return captor.getValue();
    }
}
