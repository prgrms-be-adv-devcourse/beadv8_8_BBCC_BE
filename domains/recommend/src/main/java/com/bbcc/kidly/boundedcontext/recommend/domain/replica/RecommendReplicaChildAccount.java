package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 계정 → 자녀 프로필 · 보호자 복사본이다(child-account.changed). 자녀 토큰으로 부른 추천을 그 계정의 자녀 프로필로 계산할 때 읽는다.
 * 계정 ID는 다시 쓰이지 않고 한 계정의 프로필 · 보호자는 바뀌지 않으므로, 처음 받은 값을 그대로 두고 삭제만 한 방향(있음 → 삭제)으로 반영한다.
 * 그래서 같은 메시지를 다시 받거나 생성 · 삭제가 거꾸로 도착해도 결과가 같다.
 * 삭제된 계정도 조회되어야 늦게 도착한 생성 메시지로 되살아나지 않으므로 {@code @SQLRestriction}을 붙이지 않는다.
 */
@Entity
@Table(schema = "recommend", name = "replica_child_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaChildAccount extends BaseSoftDeleteEntity {

    // 원래 계정 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    public record ChangeInfo(Long accountId, Long childProfileId, Long guardianId, boolean deleted,
        LocalDateTime occurredAt) {
    }

    public static RecommendReplicaChildAccount create(ChangeInfo info) {
        RecommendReplicaChildAccount account = new RecommendReplicaChildAccount();
        account.accountId = requireNonNull(info.accountId(), "accountId는 필수입니다");
        account.childProfileId = requireNonNull(info.childProfileId(), "childProfileId는 필수입니다");
        account.guardianId = requireNonNull(info.guardianId(), "guardianId는 필수입니다");
        account.applyDeletion(info);
        return account;
    }

    // 생성 메시지는 이미 있는 복사본을 바꾸지 않는다. 삭제는 처음 반영한 시각을 유지한다
    public void applyDeletion(ChangeInfo info) {
        if (info.deleted()) {
            delete(requireNonNull(info.occurredAt(), "occurredAt은 필수입니다").truncatedTo(ChronoUnit.MICROS));
        }
    }

    /**
     * 이 보호자가 연결한 살아 있는 계정이면 그 자녀 프로필 ID를 돌려준다. 삭제됐거나 보호자가 다르면 빈 값이다.
     */
    public Optional<Long> childProfileIdFor(Long guardianId) {
        if (isDeleted() || !this.guardianId.equals(guardianId)) {
            return Optional.empty();
        }
        return Optional.of(childProfileId);
    }
}
