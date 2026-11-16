package com.bbcc.kidly.boundedcontext.review.out.favorite;

import com.bbcc.kidly.boundedcontext.favorite.app.port.in.ChildFavoritesRemover;
import com.bbcc.kidly.boundedcontext.review.app.port.ChildFavorites;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 자녀 계정 삭제 때 찜 정리를 찜 컨텍스트의 들어오는 포트에 맡긴다(같은 트랜잭션에 합류).
 */
@Component
@RequiredArgsConstructor
public class FavoriteChildFavorites implements ChildFavorites {

    private final ChildFavoritesRemover childFavoritesRemover;

    @Override
    public int removeAll(Long accountId, Long guardianId, LocalDateTime deletedAt) {
        return childFavoritesRemover.removeAll(accountId, guardianId, deletedAt);
    }
}
