package com.bbcc.kidly.boundedcontext.wish.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.wish.app.dto.AddWishItemResult;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog.Child;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.Status;
import com.bbcc.kidly.boundedcontext.wish.out.actor.ParentWishActor;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishServiceTest {

    private static final Long PARENT_ID = 1L;
    private static final Long SELLER_ID = 2L;
    private static final Long OTHER_PARENT_ID = 3L;
    private static final Long CHILD_ID = 7L;
    private static final Long SIBLING_ID = 8L;
    private static final Long OPTION_ID = 1L;
    private static final Long WISH_ITEM_ID = 10L;

    private static final ParentWishActor PARENT = new ParentWishActor(PARENT_ID);

    @Mock
    private WishItemRepository wishItemRepository;

    @Mock
    private WishCatalog wishCatalog;

    private WishService wishService;

    @BeforeEach
    void setUp() {
        wishService = new WishService(wishItemRepository, wishCatalog, new WishPolicy());
        when(wishCatalog.findProductOption(OPTION_ID)).thenReturn(Optional.of(product(OPTION_ID, true)));
        when(wishCatalog.findSellerName(SELLER_ID)).thenReturn(Optional.of("판매자"));
        when(wishCatalog.findChild(CHILD_ID)).thenReturn(Optional.of(new Child(CHILD_ID, PARENT_ID, false)));
        when(wishCatalog.findChild(SIBLING_ID)).thenReturn(Optional.of(new Child(SIBLING_ID, PARENT_ID, false)));
        when(wishItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("담기")
    class Add {

        @Test
        @DisplayName("새 옵션이면 부모가 담은 새 줄을 만든다")
        void createsLine() {
            AddWishItemResult result = wishService.add(PARENT, CHILD_ID, OPTION_ID, 2);

            assertThat(result.created()).isTrue();
            assertThat(result.line().childId()).isEqualTo(CHILD_ID);
            assertThat(result.line().quantity()).isEqualTo(2);
            assertThat(result.line().lineAmount()).isEqualTo(50_000);
            assertThat(result.line().sellerName()).isEqualTo("판매자");
            assertThat(result.line().addedBy()).isEqualTo("PARENT");
        }

        @Test
        @DisplayName("자녀가 담으면 담은 사람이 자녀다")
        void childAdds() {
            AddWishItemResult result = wishService.add(new TestChildWishActor(CHILD_ID), CHILD_ID, OPTION_ID, 1);

            assertThat(result.line().addedBy()).isEqualTo("CHILD");
        }

        @Test
        @DisplayName("같은 옵션을 다시 담으면 있던 줄에 수량을 더한다(새 줄 없음)")
        void mergesSameOption() {
            WishItem existing = WishItem.create(CHILD_ID, OPTION_ID, 2, AddedBy.CHILD, CHILD_ID);
            when(wishItemRepository.findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(CHILD_ID, OPTION_ID,
                Status.ACTIVE)).thenReturn(Optional.of(existing));

            AddWishItemResult result = wishService.add(PARENT, CHILD_ID, OPTION_ID, 3);

            assertThat(result.created()).isFalse();
            assertThat(existing.getQuantity()).isEqualTo(5);
            assertThat(result.line().addedBy()).isEqualTo("CHILD");
            verify(wishItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("합친 수량이 99를 넘으면 거절한다")
        void mergeOverMax() {
            when(wishItemRepository.findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(CHILD_ID, OPTION_ID,
                Status.ACTIVE)).thenReturn(Optional.of(WishItem.create(CHILD_ID, OPTION_ID, 98, AddedBy.PARENT,
                PARENT_ID)));

            assertCode(() -> wishService.add(PARENT, CHILD_ID, OPTION_ID, 2), WishErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("합치지 않는 정책이면 같은 옵션도 새 줄이다(정책을 갈아 끼움)")
        void separateLinesPolicy() {
            WishService separate = new WishService(wishItemRepository, wishCatalog, new WishPolicy() {
                @Override
                public boolean mergesSameOption() {
                    return false;
                }
            });
            when(wishItemRepository.findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(CHILD_ID, OPTION_ID,
                Status.ACTIVE)).thenReturn(Optional.of(WishItem.create(CHILD_ID, OPTION_ID, 1, AddedBy.PARENT,
                PARENT_ID)));

            assertThat(separate.add(PARENT, CHILD_ID, OPTION_ID, 1).created()).isTrue();
        }

        @Test
        @DisplayName("최대 줄 수 바로 아래면 담고, 최대 줄 수면 새 줄을 거절한다")
        void maxLines() {
            when(wishItemRepository.countByChildIdAndStatus(CHILD_ID, Status.ACTIVE))
                .thenReturn((long) WishPolicy.MAX_LINES - 1, (long) WishPolicy.MAX_LINES);

            assertThat(wishService.add(PARENT, CHILD_ID, OPTION_ID, 1).created()).isTrue();
            assertCode(() -> wishService.add(PARENT, CHILD_ID, OPTION_ID, 1), WishErrorCode.WISH_ITEM_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("최대 줄 수여도 같은 옵션 합치기는 된다(줄이 늘지 않음)")
        void mergeAtMaxLines() {
            when(wishItemRepository.countByChildIdAndStatus(CHILD_ID, Status.ACTIVE))
                .thenReturn((long) WishPolicy.MAX_LINES);
            when(wishItemRepository.findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(CHILD_ID, OPTION_ID,
                Status.ACTIVE)).thenReturn(Optional.of(WishItem.create(CHILD_ID, OPTION_ID, 1, AddedBy.PARENT,
                PARENT_ID)));

            assertThat(wishService.add(PARENT, CHILD_ID, OPTION_ID, 1).created()).isFalse();
        }

        @Test
        @DisplayName("다른 부모의 자녀 · 삭제된 자녀 · 없는 자녀에는 담을 수 없다(없는 자녀)")
        void rejectsUnusableChild() {
            when(wishCatalog.findChild(20L)).thenReturn(Optional.of(new Child(20L, OTHER_PARENT_ID, false)));
            when(wishCatalog.findChild(21L)).thenReturn(Optional.of(new Child(21L, PARENT_ID, true)));

            assertCode(() -> wishService.add(PARENT, 20L, OPTION_ID, 1), WishErrorCode.CHILD_NOT_FOUND);
            assertCode(() -> wishService.add(PARENT, 21L, OPTION_ID, 1), WishErrorCode.CHILD_NOT_FOUND);
            assertCode(() -> wishService.add(PARENT, 22L, OPTION_ID, 1), WishErrorCode.CHILD_NOT_FOUND);
            verify(wishItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("자녀는 형제의 공용 장바구니에 담을 수 없다")
        void childCannotUseSibling() {
            assertCode(() -> wishService.add(new TestChildWishActor(CHILD_ID), SIBLING_ID, OPTION_ID, 1),
                WishErrorCode.CHILD_NOT_FOUND);
        }

        @Test
        @DisplayName("장바구니와 같은 판단: 없는 옵션 · 자기 상품 · 구매 불가 상품은 거절한다")
        void sameChecksAsCart() {
            when(wishCatalog.findProductOption(9L)).thenReturn(Optional.of(product(9L, false)));

            assertCode(() -> wishService.add(PARENT, CHILD_ID, 99L, 1), WishErrorCode.PRODUCT_OPTION_NOT_FOUND);
            assertCode(() -> wishService.add(new ParentWishActor(SELLER_ID), CHILD_ID, OPTION_ID, 1),
                WishErrorCode.OWN_PRODUCT);
            assertCode(() -> wishService.add(PARENT, CHILD_ID, 9L, 1), WishErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
    }

    @Nested
    @DisplayName("보기")
    class View {

        @Test
        @DisplayName("상품 복사본이 없는 줄은 상품 칸이 비고, 합계는 구매 가능한 줄만 더한다")
        void lines() {
            WishItem onSale = WishItem.create(CHILD_ID, OPTION_ID, 2, AddedBy.PARENT, PARENT_ID);
            WishItem stopped = WishItem.create(CHILD_ID, 9L, 1, AddedBy.CHILD, CHILD_ID);
            WishItem gone = WishItem.create(CHILD_ID, 99L, 1, AddedBy.CHILD, CHILD_ID);
            when(wishItemRepository.findAllByChildIdAndStatusOrderByIdDesc(CHILD_ID, Status.ACTIVE))
                .thenReturn(List.of(onSale, stopped, gone));
            when(wishCatalog.findProductOptions(List.of(OPTION_ID, 9L, 99L)))
                .thenReturn(List.of(product(OPTION_ID, true), product(9L, false)));
            when(wishCatalog.findSellerNames(any())).thenReturn(Map.of(SELLER_ID, "판매자"));

            WishView view = wishService.getWishes(PARENT, CHILD_ID);

            assertThat(view.childId()).isEqualTo(CHILD_ID);
            assertThat(view.items()).extracting(WishView.Line::purchasable).containsExactly(true, false, false);
            assertThat(view.items().get(2).productName()).isNull();
            assertThat(view.items().get(2).lineAmount()).isZero();
            assertThat(view.totalAmount()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("다른 부모의 자녀 공용 장바구니는 볼 수 없다")
        void rejectsOtherParent() {
            assertCode(() -> wishService.getWishes(new ParentWishActor(OTHER_PARENT_ID), CHILD_ID),
                WishErrorCode.CHILD_NOT_FOUND);
            verify(wishItemRepository, never()).findAllByChildIdAndStatusOrderByIdDesc(any(), any());
        }
    }

    @Nested
    @DisplayName("빼기")
    class Remove {

        @Test
        @DisplayName("부모는 자녀가 담은 줄도 뺀다")
        void parentRemoves() {
            WishItem item = stored(WishItem.create(CHILD_ID, OPTION_ID, 1, AddedBy.CHILD, CHILD_ID));

            wishService.remove(PARENT, WISH_ITEM_ID);

            verify(wishItemRepository).delete(item);
        }

        @Test
        @DisplayName("자녀는 부모가 담은 줄을 뺄 수 없다(정책)")
        void childCannotRemoveParentLine() {
            stored(WishItem.create(CHILD_ID, OPTION_ID, 1, AddedBy.PARENT, PARENT_ID));

            assertCode(() -> wishService.remove(new TestChildWishActor(CHILD_ID), WISH_ITEM_ID),
                WishErrorCode.WISH_ITEM_REMOVE_FORBIDDEN);
            verify(wishItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("다른 부모의 자녀 줄은 없는 줄이다")
        void otherParentLineNotFound() {
            stored(WishItem.create(CHILD_ID, OPTION_ID, 1, AddedBy.PARENT, PARENT_ID));

            assertCode(() -> wishService.remove(new ParentWishActor(OTHER_PARENT_ID), WISH_ITEM_ID),
                WishErrorCode.WISH_ITEM_NOT_FOUND);
            verify(wishItemRepository, never()).delete(any());
        }

        @Test
        @DisplayName("없는 줄은 없는 줄이다")
        void notFound() {
            assertCode(() -> wishService.remove(PARENT, WISH_ITEM_ID), WishErrorCode.WISH_ITEM_NOT_FOUND);
        }

        private WishItem stored(WishItem item) {
            when(wishItemRepository.findByIdAndStatus(WISH_ITEM_ID, Status.ACTIVE)).thenReturn(Optional.of(item));
            return item;
        }
    }

    private static ProductOption product(Long optionId, boolean purchasable) {
        return new ProductOption(optionId, 1L, SELLER_ID, "상품 A", "네이비 / 110", "110", 25_000L, purchasable);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
