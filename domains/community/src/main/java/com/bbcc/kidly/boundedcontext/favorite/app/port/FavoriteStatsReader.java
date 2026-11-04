package com.bbcc.kidly.boundedcontext.favorite.app.port;

import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteStats;
import java.util.Optional;

/**
 * 상품별 찜 수 읽기. 구현은 out/persistence(Spring Data가 만든다).
 */
public interface FavoriteStatsReader {

    Optional<FavoriteStats> findById(Long productId);
}
