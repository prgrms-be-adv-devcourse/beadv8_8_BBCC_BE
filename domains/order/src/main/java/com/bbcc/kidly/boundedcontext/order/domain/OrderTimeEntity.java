package com.bbcc.kidly.boundedcontext.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 주문 엔티티의 생성 · 수정 시각(created_at, updated_at). 공통 BaseTimeEntity(JPA Auditing)와 같은 시점 · 값으로 채운다:
 * 저장할 때 둘을 같은 시각으로, 바뀐 엔티티를 flush할 때 수정 시각만. 주문 domain이 공통 모듈 · Spring을 쓰지 않게 JPA 콜백으로 둔다(#531).
 */
@Getter
@MappedSuperclass
public abstract class OrderTimeEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void recordCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void recordUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
}
