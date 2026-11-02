package com.bbcc.kidly.boundedcontext.review.out.favorite;

import com.bbcc.kidly.boundedcontext.favorite.app.port.in.MemberFavoritesRemover;
import com.bbcc.kidly.boundedcontext.review.app.port.MemberFavorites;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 찜 정리를 찜 컨텍스트의 들어오는 포트에 맡긴다. 찜 컨텍스트를 아는 것은 이 어댑터뿐이다(같은 트랜잭션에 합류).
 */
@Component
@RequiredArgsConstructor
public class FavoriteMemberFavorites implements MemberFavorites {

    private final MemberFavoritesRemover memberFavoritesRemover;

    @Override
    public int removeAll(Long memberId) {
        return memberFavoritesRemover.removeAll(memberId);
    }
}
