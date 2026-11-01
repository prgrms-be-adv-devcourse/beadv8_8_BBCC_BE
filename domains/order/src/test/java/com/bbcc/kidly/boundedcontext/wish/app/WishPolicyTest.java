package com.bbcc.kidly.boundedcontext.wish.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WishPolicyTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 7L;
    private static final Long SIBLING_ID = 8L;

    private final WishPolicy policy = new WishPolicy();

    @Test
    @DisplayName("부모는 자녀가 담은 줄도 뺄 수 있다")
    void parentRemovesAny() {
        WishItem byChild = WishItem.create(CHILD_ID, 1L, 1, AddedBy.CHILD, CHILD_ID);

        assertThat(policy.canRemove(new ParentWishActor(PARENT_ID), byChild)).isTrue();
    }

    @Test
    @DisplayName("자녀는 자기가 담은 줄만 뺄 수 있다(부모 · 다른 자녀가 담은 줄은 못 뺀다)")
    void childRemovesOwnOnly() {
        TestChildWishActor child = new TestChildWishActor(CHILD_ID);

        assertThat(policy.canRemove(child, WishItem.create(CHILD_ID, 1L, 1, AddedBy.CHILD, CHILD_ID))).isTrue();
        assertThat(policy.canRemove(child, WishItem.create(CHILD_ID, 1L, 1, AddedBy.PARENT, PARENT_ID))).isFalse();
        assertThat(policy.canRemove(child, WishItem.create(CHILD_ID, 1L, 1, AddedBy.CHILD, SIBLING_ID))).isFalse();
        // 부모 회원 ID와 자녀 프로필 ID가 같은 숫자여도 종류가 다르면 남이 담은 줄
        assertThat(policy.canRemove(child, WishItem.create(CHILD_ID, 1L, 1, AddedBy.PARENT, CHILD_ID))).isFalse();
    }

    @Test
    @DisplayName("기본값: 같은 옵션은 합치고, 자녀 한 명당 50줄까지")
    void defaults() {
        assertThat(policy.mergesSameOption()).isTrue();
        assertThat(policy.maxLines()).isEqualTo(50);
    }
}
