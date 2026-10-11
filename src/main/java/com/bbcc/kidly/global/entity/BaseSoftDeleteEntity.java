package com.bbcc.kidly.global.entity;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

/**
 * 생성·수정 시각에 더해 삭제 시각(deleted_at)을 기록한다. 행을 지우지 않고 삭제 표시만 하는 엔티티는 이 클래스를 상속한다.
 * 삭제된 행을 조회에서 빼려면 엔티티에 {@code @SQLRestriction("deleted_at IS NULL")}을 붙인다.
 */
@Getter
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseTimeEntity {

    private Instant deletedAt;

    /**
     * 삭제 표시를 한다. 이미 삭제됐으면 처음 삭제 시각을 유지한다.
     */
    public void delete(Instant now) {
        if (isDeleted()) {
            return;
        }
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
