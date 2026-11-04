package com.bbcc.kidly.boundedcontext.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 찜((회원, 상품)당 1개). 판매 종료 상품은 새로 찜할 수 없다. 찜한 뒤 판매 종료돼도 찜은 남는다.
 */
@Entity(name = "CommunityFavorite")
@Table(schema = "community", name = "favorite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends FavoriteTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long productId;

    /**
     * @param productSaleEnded 상품이 판매 종료인지. 판매 종료면 찜할 수 없다
     */
    public static Favorite create(Long memberId, Long productId, boolean productSaleEnded) {
        if (productSaleEnded) {
            throw new FavoriteRuleException(FavoriteRule.SALE_ENDED);
        }
        Favorite favorite = new Favorite();
        favorite.memberId = memberId;
        favorite.productId = productId;
        return favorite;
    }
}
