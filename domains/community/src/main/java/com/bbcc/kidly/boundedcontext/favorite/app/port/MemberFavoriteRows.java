package com.bbcc.kidly.boundedcontext.favorite.app.port;

import java.util.List;

/**
 * 한 가족 · 한 자녀 계정 단위 찜 행 다루기(회원 탈퇴 · 자녀 계정 삭제). 구현은 out/persistence(Spring Data가 만든다).
 * 둘 다 한 문장으로 지우며 실제로 지운 행을 돌려준다(delete … returning). 같은 소유자의 찜 해제와 겹쳐도 한쪽만 지우고,
 * 지운 뒤 새로 들어온 찜은 지우지도 세지도 않아 찜 수와 어긋나지 않는다.
 */
public interface MemberFavoriteRows {

    /**
     * 그 보호자 회원의 찜(회원 본인 찜 + 그 회원이 보호자인 자녀 계정 찜)을 모두 지운다.
     * 보호자 탈퇴 뒤에는 자녀 프로필 삭제(child.changed) → 인증이 자녀 계정 삭제 · 토큰 무효화 · child-account.changed(deleted=true)
     * → 커뮤니티가 그 자녀 계정 찜 정리로도 이어지지만, 탈퇴 때 이미 여기서 지우므로 그쪽은 지울 찜이 없다(1회분).
     *
     * @return 지운 찜(지운 행마다 하나)
     */
    List<DeletedFavorite> deleteAllByGuardian(Long guardianId);

    /**
     * 그 자녀 계정의 찜을 모두 지운다.
     *
     * @return 지운 찜(지운 행마다 하나)
     */
    List<DeletedFavorite> deleteAllByChild(Long accountId);

    /**
     * 지운 찜 한 행.
     */
    interface DeletedFavorite {

        Long getId();

        Long getProductId();

        String getOwnerType();

        Long getOwnerId();

        Long getGuardianId();
    }
}
