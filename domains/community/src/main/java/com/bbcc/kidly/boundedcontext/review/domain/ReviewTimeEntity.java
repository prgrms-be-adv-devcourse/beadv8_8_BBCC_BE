package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 리뷰 컨텍스트 엔티티의 생성 · 수정 시각(created_at, updated_at). 공통 BaseTimeEntity(JPA Auditing)와 같은 시점 · 값으로 채운다:
 * 저장할 때 둘을 같은 시각으로, 바뀐 엔티티를 flush할 때 수정 시각만. domain이 공통 모듈 · Spring을 쓰지 않게 JPA 콜백으로 둔다.
 */
@Getter
@MappedSuperclass
public abstract class ReviewTimeEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void recordCreated() {
        LocalDateTime now = ReviewTimes.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void recordUpdated() {
        if (updatesTimeOnFlush()) {
            this.updatedAt = ReviewTimes.now();
        }
    }

    /**
     * 바뀐 엔티티를 flush할 때 수정 시각을 자동으로 바꾸는지. 기본은 바꾼다. 수정 시각을 정해진 변경에서만 바꾸는 엔티티는
     * false를 돌려주고 그 변경에서 {@link #touch()}를 부른다.
     */
    protected boolean updatesTimeOnFlush() {
        return true;
    }

    /**
     * 수정 시각을 지금으로 바꾼다(자동 갱신을 끈 엔티티의 정해진 변경 · 딸린 값만 바뀐 변경).
     */
    protected void touch() {
        this.updatedAt = ReviewTimes.now();
    }
}
