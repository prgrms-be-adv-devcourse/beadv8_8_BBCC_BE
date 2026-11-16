package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;

/**
 * 찜 API를 부른 주체(in이 주체 헤더로 만든다). 회원 본인이면 child false · id = 회원 ID, 자녀 계정이면 child true · id = 자녀 계정 ID.
 *
 * @param guardianId 회원 본인이면 본인 회원 ID, 자녀 계정이면 보호자 회원 ID
 */
public record FavoriteActor(boolean child, Long id, Long guardianId) {

    public static FavoriteActor member(Long memberId) {
        return new FavoriteActor(false, memberId, memberId);
    }

    public static FavoriteActor child(Long accountId, Long guardianId) {
        return new FavoriteActor(true, accountId, guardianId);
    }

    FavoriteOwner toOwner() {
        return child ? FavoriteOwner.child(id, guardianId) : FavoriteOwner.member(id);
    }
}
