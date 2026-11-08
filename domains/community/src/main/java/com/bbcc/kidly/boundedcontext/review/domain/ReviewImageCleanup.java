package com.bbcc.kidly.boundedcontext.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저장소에서 지울 차례를 기다리는 리뷰 사진 한 장(정리 대기). 리뷰 수정으로 빠지거나 리뷰 삭제로 남은 사진의 key를 그 트랜잭션에서 넣고
 * (같은 key는 한 행), 정리 스케줄러가 지운 뒤 행을 지운다. 실패하면 시도 횟수 · 다음 시각 · 실패 종류만 바꾼다.
 * 넣기 · 바꾸기는 저장소의 조건부 문장으로 하고 이 엔티티는 읽기에 쓴다.
 */
@Entity(name = "CommunityReviewImageCleanup")
@Table(schema = "community", name = "review_image_cleanup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImageCleanup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(length = 200)
    private String lastFailureType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 정리 대기에 들어온 까닭.
     */
    public enum Reason {
        // 리뷰 수정으로 목록에서 빠짐
        REPLACED,
        // 리뷰 삭제로 남음
        REVIEW_DELETED
    }
}
