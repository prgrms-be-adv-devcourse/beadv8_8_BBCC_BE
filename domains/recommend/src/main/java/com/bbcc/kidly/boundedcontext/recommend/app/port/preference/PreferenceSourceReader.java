package com.bbcc.kidly.boundedcontext.recommend.app.port.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.ConfirmedPurchase;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.RecentActivity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 선호를 계산하는 배치가 읽는 활동 기록 · 구매 기록 · 상품 벡터를 읽는 나가는 연결의 요청서다.
 * 기간은 호출하는 쪽이 시각으로 넘기고, 기간의 양 끝 시각은 기간에 포함한다.
 */
public interface PreferenceSourceReader {

    /**
     * 활동 기간 안에 활동했거나 구매 기간 안에 구매확정한 자녀의 ID를 중복 없이 ID 오름차순으로 읽는다.
     * afterChildProfileId보다 큰 ID만 읽어 다음 묶음을 이어 읽는다.
     * 자녀 복사본이 삭제된 자녀는 빼고, 복사본이 아직 없는 자녀는 대상이다.
     */
    List<Long> findChildTargets(LocalDateTime activitySince, LocalDateTime purchaseSince,
        long afterChildProfileId, int limit);

    /**
     * 활동 기간 안에 자녀를 고르지 않고 활동한 회원의 ID를 중복 없이 ID 오름차순으로 읽는다.
     */
    List<Long> findMemberTargets(LocalDateTime activitySince, long afterMemberId, int limit);

    /**
     * 자녀의 검색 · 상품 상세 진입 · 검색 결과 클릭 · 장바구니 담기 · 조르기를 발생 시각 순으로 읽는다.
     * 장바구니 빼기 · 조르기 철회 기록은 돌려주지 않고, 기간 끝까지 뺀 담기 · 직접 철회한 조르기에 withdrawn을 표시한다.
     * 카테고리와 브랜드는 활동 기록 값을 쓰고, 없으면 상품 복사본의 지금 값을 쓴다.
     */
    List<RecentActivity> findChildActivities(Long childProfileId, LocalDateTime from, LocalDateTime to);

    /**
     * 자녀를 고르지 않은 회원의 활동을 자녀와 같은 방식으로 읽는다. 찜은 회원 단위라 여기서만 읽히고,
     * 찜 해제 기록은 돌려주지 않고 기간 끝까지 해제한 찜에 withdrawn을 표시한다.
     */
    List<RecentActivity> findMemberActivities(Long memberId, LocalDateTime from, LocalDateTime to);

    /**
     * 자녀의 구매확정을 주문상품 한 줄이 한 건으로 읽는다. 브랜드는 상품 복사본에서 가져온다.
     */
    List<ConfirmedPurchase> findChildPurchases(Long childProfileId, LocalDateTime from, LocalDateTime to);

    /**
     * 상품의 임베딩 벡터를 읽는다. 벡터가 없는 상품은 담지 않는다.
     */
    Map<Long, EmbeddingVector> findEmbeddings(Collection<Long> productIds);
}
