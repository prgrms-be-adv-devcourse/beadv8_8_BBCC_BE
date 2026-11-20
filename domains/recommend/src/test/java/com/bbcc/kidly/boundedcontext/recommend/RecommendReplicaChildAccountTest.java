package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildAccount;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendReplicaChildAccountTest {

    private static final Long ACCOUNT_ID = 501L;
    private static final Long CHILD_PROFILE_ID = 11L;
    private static final Long GUARDIAN_ID = 1L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Test
    @DisplayName("살아 있는 계정은 그 보호자에게만 연결된 자녀 프로필 ID를 돌려준다")
    void childProfileIdForGuardian() {
        RecommendReplicaChildAccount account = RecommendReplicaChildAccount.create(created());

        assertThat(account.childProfileIdFor(GUARDIAN_ID)).contains(CHILD_PROFILE_ID);
        assertThat(account.childProfileIdFor(GUARDIAN_ID + 1)).isEmpty();
    }

    @Test
    @DisplayName("삭제 메시지를 받으면 보호자가 맞아도 자녀 프로필 ID를 돌려주지 않는다")
    void deletedAccountHasNoChildProfile() {
        RecommendReplicaChildAccount account = RecommendReplicaChildAccount.create(created());

        account.applyDeletion(deleted(CREATED_AT.plusDays(1)));

        assertThat(account.isDeleted()).isTrue();
        assertThat(account.childProfileIdFor(GUARDIAN_ID)).isEmpty();
    }

    @Test
    @DisplayName("삭제가 먼저 도착해 만든 복사본은 늦게 온 생성 메시지로 되살아나지 않고, 처음 삭제 시각을 유지한다")
    void lateCreationDoesNotRevive() {
        LocalDateTime deletedAt = CREATED_AT.plusDays(1);
        RecommendReplicaChildAccount account = RecommendReplicaChildAccount.create(deleted(deletedAt));

        account.applyDeletion(created());
        account.applyDeletion(deleted(deletedAt.plusDays(1)));

        assertThat(account.childProfileIdFor(GUARDIAN_ID)).isEmpty();
        assertThat(account.getDeletedAt()).isEqualTo(deletedAt);
    }

    private static RecommendReplicaChildAccount.ChangeInfo created() {
        return new RecommendReplicaChildAccount.ChangeInfo(
            ACCOUNT_ID, CHILD_PROFILE_ID, GUARDIAN_ID, false, CREATED_AT);
    }

    private static RecommendReplicaChildAccount.ChangeInfo deleted(LocalDateTime at) {
        return new RecommendReplicaChildAccount.ChangeInfo(ACCOUNT_ID, CHILD_PROFILE_ID, GUARDIAN_ID, true, at);
    }
}
