package com.bbcc.kidly.boundedcontext.order.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaCartItemRepositoryIntegrationTest {

    private static final Long BUYER_ID = 1L;
    private static final Long OTHER_ID = 3L;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 줄 찾기: 자녀가 null이면 자녀 없는 줄만 찾는다 (IS NULL)")
    void findsLineWithoutChild() {
        CartItem withoutChild = cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1));
        cartItemRepository.save(CartItem.create(BUYER_ID, 1L, 1L, 1));

        assertThat(cartItemRepository.findByMemberIdAndOptionIdAndChildId(BUYER_ID, 1L, null))
            .get().extracting(CartItem::getId).isEqualTo(withoutChild.getId());
    }

    @Test
    @DisplayName("같은 줄 찾기: 자녀를 지정하면 그 자녀의 줄만 찾는다")
    void findsLineWithChild() {
        cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1));
        CartItem withChild = cartItemRepository.save(CartItem.create(BUYER_ID, 1L, 1L, 1));

        assertThat(cartItemRepository.findByMemberIdAndOptionIdAndChildId(BUYER_ID, 1L, 1L))
            .get().extracting(CartItem::getId).isEqualTo(withChild.getId());
    }

    @Test
    @DisplayName("다른 회원의 줄은 id로 찾지 않는다")
    void findByIdAndMemberId() {
        CartItem item = cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1));

        assertThat(cartItemRepository.findByIdAndMemberId(item.getId(), BUYER_ID)).isPresent();
        assertThat(cartItemRepository.findByIdAndMemberId(item.getId(), OTHER_ID)).isEmpty();
    }

    @Test
    @DisplayName("회원의 줄을 최근에 담은 줄부터 돌려준다")
    void findAllNewestFirst() {
        CartItem first = cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1));
        cartItemRepository.save(CartItem.create(OTHER_ID, 1L, null, 1));
        CartItem second = cartItemRepository.save(CartItem.create(BUYER_ID, 2L, null, 1));

        List<CartItem> items = cartItemRepository.findAllByMemberIdOrderByIdDesc(BUYER_ID);

        assertThat(items).extracting(CartItem::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("(회원, 옵션, 자녀) 유니크 인덱스가 NULLS NOT DISTINCT로 만들어진다")
    void uniqueIndexIsGenerated() {
        String indexDef = jdbcTemplate.queryForObject(
            "select indexdef from pg_indexes where schemaname = 'orders' and indexname = ?",
            String.class, "uk_cart_item_member_option_child");

        assertThat(indexDef).contains("UNIQUE").contains("(member_id, option_id, child_id)")
            .contains("NULLS NOT DISTINCT");
    }

    @Test
    @DisplayName("자녀 없는 같은 줄을 두 번 저장할 수 없다")
    void rejectsDuplicateLineWithoutChild() {
        cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1));

        assertThatThrownBy(() -> cartItemRepository.save(CartItem.create(BUYER_ID, 1L, null, 1)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
