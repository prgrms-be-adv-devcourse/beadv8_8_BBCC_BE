package com.bbcc.kidly.boundedcontext.recommend.app.port.preference;

import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import java.time.LocalDateTime;

/**
 * 자녀 · 회원별로 계산한 카테고리 · 브랜드 친밀도와 취향 벡터를 저장하고 읽는 나가는 연결의 요청서다.
 */
public interface PreferenceSnapshotStore {

    /**
     * 그 자녀의 친밀도 줄과 취향 벡터를 지우고 스냅숏으로 새로 넣는다. 지우기와 넣기는 한 트랜잭션이다.
     * 스냅숏의 취향 벡터가 null이면 저장된 취향 벡터만 지운다.
     */
    void replaceChild(Long childProfileId, PreferenceSnapshot snapshot);

    /**
     * 그 회원의 친밀도 줄과 취향 벡터를 지우고 스냅숏으로 새로 넣는다. 자녀를 고르지 않은 활동으로 계산한 값이다.
     */
    void replaceMember(Long memberId, PreferenceSnapshot snapshot);

    /**
     * 저장된 자녀의 스냅숏을 읽는다. 저장된 줄이 없으면 빈 스냅숏이다.
     * 계산 시각은 읽은 줄 중 가장 늦은 시각이고, 카테고리는 ID 순, 브랜드는 이름 순이다.
     */
    PreferenceSnapshot findChild(Long childProfileId);

    /**
     * 저장된 회원의 스냅숏을 읽는다. 읽는 방식은 자녀와 같다.
     */
    PreferenceSnapshot findMember(Long memberId);

    /**
     * 계산 시각이 baseTime보다 이른 줄을 자녀 · 회원의 친밀도와 취향 벡터 표에서 모두 지운다.
     * 이번 계산이 다시 쓰지 않은 대상(탈퇴한 회원, 삭제한 자녀, 기간 안에 활동이 없어진 대상)의 줄을 정리한다.
     *
     * @return 지운 줄 수의 합
     */
    int deleteCalculatedBefore(LocalDateTime baseTime);
}
