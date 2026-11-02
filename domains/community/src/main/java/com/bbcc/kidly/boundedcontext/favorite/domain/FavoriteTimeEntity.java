package com.bbcc.kidly.boundedcontext.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 찜 컨텍스트 엔티티의 생성 · 수정 시각(created_at, updated_at). 저장할 때 둘을 같은 시각으로, 바뀐 엔티티를 flush할 때 수정 시각만.
 * domain이 공통 모듈 · Spring을 쓰지 않게 JPA 콜백으로 둔다. 시각은 마이크로초로 자른다(FavoriteTimes).
 */
@Getter
@MappedSuperclass
public abstract class FavoriteTimeEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void recordCreated() {
        LocalDateTime now = FavoriteTimes.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void recordUpdated() {
        this.updatedAt = FavoriteTimes.now();
    }
}
