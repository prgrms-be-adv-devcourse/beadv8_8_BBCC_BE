package com.bbcc.kidly.boundedcontext.wish.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.Status;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WishItemTest {

    @Test
    @DisplayName("만들면 ACTIVE이고 자녀 · 옵션 · 수량 · 담은 사람을 갖는다")
    void create() {
        WishItem item = WishItem.create(7L, 1L, 2, AddedBy.CHILD, 7L);

        assertThat(item.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(item.getChildId()).isEqualTo(7L);
        assertThat(item.getOptionId()).isEqualTo(1L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getAddedByType()).isEqualTo(AddedBy.CHILD);
        assertThat(item.getAddedById()).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("수량이 1보다 작으면 만들 수 없다")
    void createRejectsQuantity(int quantity) {
        assertThatThrownBy(() -> WishItem.create(7L, 1L, quantity, AddedBy.PARENT, 1L))
            .isInstanceOfSatisfying(WishRuleViolationException.class,
                e -> assertThat(e.getRule()).isEqualTo(Rule.INVALID_QUANTITY));
    }

    @Test
    @DisplayName("수량을 더해도 담은 사람은 처음 값 그대로다")
    void addQuantityKeepsAddedBy() {
        WishItem item = WishItem.create(7L, 1L, 2, AddedBy.CHILD, 7L);

        item.addQuantity(3);

        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.isAddedBy(AddedBy.CHILD, 7L)).isTrue();
    }

    @Test
    @DisplayName("담은 사람은 종류와 ID가 모두 같아야 같다")
    void isAddedBy() {
        WishItem item = WishItem.create(7L, 1L, 1, AddedBy.CHILD, 7L);

        assertThat(item.isAddedBy(AddedBy.PARENT, 7L)).isFalse();
        assertThat(item.isAddedBy(AddedBy.CHILD, 8L)).isFalse();
    }
}
