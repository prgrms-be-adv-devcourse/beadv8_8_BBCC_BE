package com.bbcc.kidly.boundedcontext.cart.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.cart.CartOwners;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
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
        CartItem withoutChild = cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, null, 1));
        cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, 1L, 1));

        assertThat(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(CartItem.OwnerType.MEMBER,
            BUYER_ID, 1L, null))
            .get().extracting(CartItem::getId).isEqualTo(withoutChild.getId());
    }

    @Test
    @DisplayName("같은 줄 찾기: 자녀를 지정하면 그 자녀의 줄만 찾는다")
    void findsLineWithChild() {
        cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, null, 1));
        CartItem withChild = cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, 1L, 1));

        assertThat(cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(CartItem.OwnerType.MEMBER,
            BUYER_ID, 1L, 1L))
            .get().extracting(CartItem::getId).isEqualTo(withChild.getId());
    }

    @Test
    @DisplayName("주인(#998)이 다르면 같은 회원 · 옵션 · 자녀여도 다른 줄이다: 자녀 칸(member_id = 보호자)과 보호자 칸이 같이 있다")
    void ownersAreSeparated() {
        CartItem parentLine = cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, 7L, 1));
        CartItem childLine = cartItemRepository.save(
            CartItem.create(CartItem.OwnerType.CHILD, 7L, BUYER_ID, 1L, 7L, 2));

        assertThat(cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(CartItem.OwnerType.MEMBER, BUYER_ID))
            .extracting(CartItem::getId).containsExactly(parentLine.getId());
        assertThat(cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(CartItem.OwnerType.CHILD, 7L))
            .extracting(CartItem::getId).containsExactly(childLine.getId());
        assertThat(cartItemRepository.findById(childLine.getId())).get()
            .extracting(CartItem::getMemberId).isEqualTo(BUYER_ID);
    }

    @Test
    @DisplayName("회원의 줄을 최근에 담은 줄부터 돌려준다")
    void findAllNewestFirst() {
        CartItem first = cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, null, 1));
        cartItemRepository.save(CartOwners.memberLine(OTHER_ID, 1L, null, 1));
        CartItem second = cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 2L, null, 1));

        List<CartItem> items = cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(CartItem.OwnerType.MEMBER,
            BUYER_ID);

        assertThat(items).extracting(CartItem::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("(주인 종류, 주인, 옵션, 자녀) 유니크 인덱스가 있고 예전 (회원, 옵션, 자녀) 유니크는 없다(#998)")
    void uniqueIndexIsGenerated() {
        String indexDef = jdbcTemplate.queryForObject(
            "select indexdef from pg_indexes where schemaname = 'orders' and indexname = ?",
            String.class, "uk_cart_item_owner_option_child");

        assertThat(indexDef).contains("UNIQUE").contains("(owner_type, owner_id, option_id, child_id)");
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_indexes where schemaname = 'orders'"
            + " and indexname = 'uk_cart_item_member_option_child'", Long.class)).isZero();
    }

    @Test
    @DisplayName("자녀를 지정한 같은 줄을 두 번 저장할 수 없다")
    void rejectsDuplicateLineWithChild() {
        cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, 1L, 1));

        assertThatThrownBy(() -> cartItemRepository.save(CartOwners.memberLine(BUYER_ID, 1L, 1L, 1)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
