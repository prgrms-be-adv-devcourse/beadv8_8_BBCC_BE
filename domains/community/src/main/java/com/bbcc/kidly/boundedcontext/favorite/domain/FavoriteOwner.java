package com.bbcc.kidly.boundedcontext.favorite.domain;

import java.util.Objects;

/**
 * 찜 소유자. 회원 본인과 자녀 계정은 각자 찜을 갖고 서로의 찜을 보지 않는다. 회원 7과 자녀 계정 7은 다른 소유자다.
 *
 * @param id MEMBER는 회원 ID, CHILD는 자녀 계정 ID
 * @param guardianId MEMBER는 본인 회원 ID, CHILD는 보호자 회원 ID(보호자 탈퇴 때 함께 지우는 기준 · 이벤트 키)
 */
public record FavoriteOwner(FavoriteOwnerType type, Long id, Long guardianId) {

    public FavoriteOwner {
        Objects.requireNonNull(type);
        Objects.requireNonNull(id);
        Objects.requireNonNull(guardianId);
        if (type == FavoriteOwnerType.MEMBER && !id.equals(guardianId)) {
            throw new IllegalArgumentException("회원 본인 찜의 보호자는 본인입니다");
        }
    }

    public static FavoriteOwner member(Long memberId) {
        return new FavoriteOwner(FavoriteOwnerType.MEMBER, memberId, memberId);
    }

    public static FavoriteOwner child(Long accountId, Long guardianId) {
        return new FavoriteOwner(FavoriteOwnerType.CHILD, accountId, guardianId);
    }

    public boolean isChild() {
        return type == FavoriteOwnerType.CHILD;
    }
}
