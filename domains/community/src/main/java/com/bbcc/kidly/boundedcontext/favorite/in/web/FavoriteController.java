package com.bbcc.kidly.boundedcontext.favorite.in.web;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteQueryService;
import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteService;
import com.bbcc.kidly.boundedcontext.favorite.in.web.dto.FavoriteListResponse;
import com.bbcc.kidly.boundedcontext.favorite.in.web.dto.FavoriteSummaryResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 찜 API. 찜 · 해제 · 내 찜 목록은 로그인 필요, 찜 수 요약은 공개.
 * 오류: 400 INVALID_PRODUCT_ID · INVALID_PRODUCT_IDS · INVALID_PAGE_SIZE · INVALID_CURSOR,
 * 404 FAVORITE_PRODUCT_NOT_FOUND, 409 FAVORITE_PRODUCT_SALE_ENDED.
 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final FavoriteQueryService favoriteQueryService;

    /**
     * 찜한다. 204. 이미 찜했으면 그대로 204.
     */
    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(@LoginMember AuthMember member, @PathVariable String productId) {
        favoriteService.add(member.id(), productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 찜을 뺀다. 204. 찜이 없어도 204.
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@LoginMember AuthMember member, @PathVariable String productId) {
        favoriteService.remove(member.id(), productId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 내 찜(최신순 커서). productIds를 주면 그중 내가 찜한 것만(커서 · size 없음).
     */
    @GetMapping
    public FavoriteListResponse mine(@LoginMember AuthMember member,
        @RequestParam(required = false) String productIds, @RequestParam(required = false) String cursor,
        @RequestParam(required = false) String size) {
        return FavoriteListResponse.from(favoriteQueryService.findMine(member.id(), productIds, cursor, size));
    }

    /**
     * 상품별 찜 수(공개).
     */
    @GetMapping("/summary")
    public FavoriteSummaryResponse summary(@RequestParam(required = false) String productId) {
        var summary = favoriteQueryService.summary(productId);
        return new FavoriteSummaryResponse(String.valueOf(summary.productId()), summary.favoriteCount());
    }
}
