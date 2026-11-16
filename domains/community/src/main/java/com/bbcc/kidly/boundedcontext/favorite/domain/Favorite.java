package com.bbcc.kidly.boundedcontext.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 찜((소유자, 상품)당 1개). 소유자는 회원 본인 또는 자녀 계정이다({@link FavoriteOwner}). 판매 종료 상품은 새로 찜할 수 없다.
 * 찜한 뒤 판매 종료돼도 찜은 남는다. member_id 칸은 보호자 회원 ID(회원 본인 찜이면 본인)를 함께 남긴다(배포 중 옛 앱 호환).
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FavoriteOwnerType ownerType;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long guardianId;

    @Column(nullable = false)
    private Long productId;

    /**
     * @param productSaleEnded 상품이 판매 종료인지. 판매 종료면 찜할 수 없다
     */
    public static Favorite create(FavoriteOwner owner, Long productId, boolean productSaleEnded) {
        if (productSaleEnded) {
            throw new FavoriteRuleException(FavoriteRule.SALE_ENDED);
        }
        Favorite favorite = new Favorite();
        favorite.ownerType = owner.type();
        favorite.ownerId = owner.id();
        favorite.guardianId = owner.guardianId();
        favorite.memberId = owner.guardianId();
        favorite.productId = productId;
        return favorite;
    }

    /**
     * 회원 본인 찜으로 만든다.
     */
    public static Favorite create(Long memberId, Long productId, boolean productSaleEnded) {
        return create(FavoriteOwner.member(memberId), productId, productSaleEnded);
    }

    public FavoriteOwner owner() {
        return new FavoriteOwner(ownerType, ownerId, guardianId);
    }
}
