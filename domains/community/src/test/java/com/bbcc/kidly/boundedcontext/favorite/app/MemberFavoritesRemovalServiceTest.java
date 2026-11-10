package com.bbcc.kidly.boundedcontext.favorite.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteStatsCounter;
import com.bbcc.kidly.boundedcontext.favorite.app.port.MemberFavoriteRows;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberFavoritesRemovalServiceTest {

    private final List<String> stats = new ArrayList<>();
    private int decreasedRows = 1;
    private List<Long> deletedProductIds = List.of();

    private final MemberFavoritesRemovalService service = new MemberFavoritesRemovalService(
        (MemberFavoriteRows) memberId -> deletedProductIds.stream().map(MemberFavoritesRemovalServiceTest::deleted)
            .toList(),
        new FavoriteStatsCounter() {
            @Override
            public void increase(Long productId, LocalDateTime now) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int decreaseBy(Long productId, int count, LocalDateTime now) {
                stats.add(productId + "-" + count);
                return decreasedRows;
            }
        }, org.mockito.Mockito.mock(com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog.class));

    private static MemberFavoriteRows.DeletedFavorite deleted(Long productId) {
        return new MemberFavoriteRows.DeletedFavorite() {
            @Override
            public Long getId() {
                return productId * 10;
            }

            @Override
            public Long getProductId() {
                return productId;
            }
        };
    }

    @Test
    @DisplayName("회원 찜을 모두 지우고 실제로 지운 행 기준으로 상품별 지운 수만큼 찜 수를 줄인다")
    void removeAll() {
        deletedProductIds = List.of(14L, 15L, 14L);

        int removed = service.removeAll(7L);

        assertThat(removed).isEqualTo(3);
        assertThat(stats).containsExactlyInAnyOrder("14-2", "15-1");
    }

    @Test
    @DisplayName("지울 찜이 없으면(두 번째 탈퇴 등) 아무것도 바꾸지 않는다")
    void nothingToRemove() {
        assertThat(service.removeAll(7L)).isZero();
        assertThat(stats).isEmpty();
    }

    @Test
    @DisplayName("찜 수 행이 없으면(바뀐 행 0) 예외로 실패한다(메시지에 상품 ID만)")
    void missingStatsRowFails() {
        deletedProductIds = List.of(14L);
        decreasedRows = 0;

        assertThatThrownBy(() -> service.removeAll(7L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("productId=14");
    }
}
