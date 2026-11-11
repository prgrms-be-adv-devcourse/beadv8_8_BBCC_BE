package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 정보 변경됨 이벤트의 카테고리 경로 한 칸이다. 카테고리는 상품 도메인의 것이라 ID만 값으로 가리키고 FK를 두지 않는다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaCategoryPathEntry {

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    // 이 카테고리를 보여 줄 자녀 성별이다. 칸이 없는 예전 메시지로 만든 행은 ALL이다
    @Enumerated(EnumType.STRING)
    @Column(name = "visible_gender", nullable = false)
    private CategoryVisibleGender visibleGender;

    public RecommendReplicaCategoryPathEntry(
        Long categoryId, String categoryName, CategoryVisibleGender visibleGender) {
        this.categoryId = requireNonNull(categoryId, "categoryId는 필수입니다");
        this.categoryName = requireNonNull(categoryName, "categoryName은 필수입니다");
        this.visibleGender = visibleGender == null ? CategoryVisibleGender.ALL : visibleGender;
    }
}
