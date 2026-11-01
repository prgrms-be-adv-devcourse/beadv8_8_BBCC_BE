package com.bbcc.kidly.boundedcontext.wish.app;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishView;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 호출 주체 자리(#640): 자녀 계정 인증은 없지만 테스트용 자녀({@link TestChildWishActor})로 부르면 자녀가 담은 줄이 되고,
 * 부모와 자녀가 같은 목록을 본다. 빼기 권한은 기본 정책을 따른다.
 */
@IntegrationTest
class WishChildActorIntegrationTest {

    private static final Long CHILD_ID = 1L;

    @Autowired
    private WishService wishService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private ChildReplicaRepository childReplicaRepository;

    @BeforeEach
    void setUp() {
        productReplicaRepository.save(ReplicaProduct.create(ReplicaFixture.onSaleProduct(25_000L, OCCURRED_AT)));
        childReplicaRepository.save(ReplicaChild.create(ReplicaFixture.child("115.0", false, OCCURRED_AT)));
    }

    @Test
    @DisplayName("자녀가 담은 줄은 담은 사람 = 자녀이고, 부모와 자녀가 같은 목록을 본다")
    void childAddsAndParentSees() {
        TestChildWishActor child = new TestChildWishActor(CHILD_ID);

        Long wishItemId = wishService.add(child, CHILD_ID, 1L, 1).line().wishItemId();

        WishView parentView = wishService.getWishes(new ParentWishActor(BUYER_ID), CHILD_ID);
        assertThat(parentView.items()).extracting(WishView.Line::wishItemId, WishView.Line::addedBy)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(wishItemId, "CHILD"));
        assertThat(wishService.getWishes(child, CHILD_ID)).isEqualTo(parentView);
    }

    @Test
    @DisplayName("자녀는 부모가 담은 줄을 뺄 수 없고(403), 자기가 담은 줄은 뺀다")
    void childRemovesOwnOnly() {
        TestChildWishActor child = new TestChildWishActor(CHILD_ID);
        Long byParent = wishService.add(new ParentWishActor(BUYER_ID), CHILD_ID, 1L, 1).line().wishItemId();

        assertThatThrownBy(() -> wishService.remove(child, byParent))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(WishErrorCode.WISH_ITEM_REMOVE_FORBIDDEN));

        wishService.remove(new ParentWishActor(BUYER_ID), byParent);
        Long byChild = wishService.add(child, CHILD_ID, 1L, 1).line().wishItemId();
        wishService.remove(child, byChild);
        assertThat(wishService.getWishes(child, CHILD_ID).items()).isEmpty();
    }
}
