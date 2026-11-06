package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자녀 계정 복사본(계정당 1행). 자녀 프로필의 활성 계정을 찾아 자녀 알림함 받는 사람을 정한다.
 * 계정 ID는 다시 쓰지 않으므로, 계정마다 있음 → 삭제됨 한 방향으로만 바뀐다. 삭제된 계정은 다시 살아나지 않는다.
 */
@Entity
@Table(schema = "notification", name = "child_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildAccountCopy extends BaseTimeEntity {

    @Id
    private Long accountId;

    @Column(nullable = false)
    private Long childProfileId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public static ChildAccountCopy create(Long accountId, Long childProfileId, boolean deleted) {
        var copy = new ChildAccountCopy();
        copy.accountId = accountId;
        copy.childProfileId = childProfileId;
        copy.deleted = deleted;
        return copy;
    }

    // 이미 삭제됐으면 그대로 둔다
    public void delete() {
        deleted = true;
    }
}
