package com.bbcc.kidly.boundedcontext.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품별 찜 수. 값은 저장소의 원자적 증감 한 문장으로만 바꾸고, 엔티티로 읽어 더해 저장하지 않는다.
 */
@Entity(name = "CommunityFavoriteStats")
@Table(schema = "community", name = "favorite_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteStats extends FavoriteTimeEntity {

    @Id
    private Long productId;

    @Column(nullable = false)
    private long favoriteCount;
}
