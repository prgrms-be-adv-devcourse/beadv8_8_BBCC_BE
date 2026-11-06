package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 정보 변경됨 이벤트의 카테고리 경로 한 칸이다. 카테고리는 상품 도메인의 것이라 ID만 값으로 가리키고 FK를 두지 않는다.
 * 상품 도메인의 카테고리와 이름이 겹치지 않게 앞에 복사본 이름을 붙인다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaCategory {

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    public RecommendReplicaCategory(Long categoryId, String categoryName) {
        this.categoryId = requireNonNull(categoryId, "categoryId는 필수입니다");
        this.categoryName = requireNonNull(categoryName, "categoryName은 필수입니다");
    }
}
