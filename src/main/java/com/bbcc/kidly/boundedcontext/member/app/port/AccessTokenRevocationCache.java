package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 회원별 Access Token 무효화 기준 시각의 조회용 사본. 요청마다 여기만 보고 DB는 보지 않는다.
 * 원본은 DB(member.access_token_revocation)다. 지금은 메모리 구현이고, 앱이 여러 대가 되면 공유 저장소(Redis 등) 구현으로 바꾼다.
 */
public interface AccessTokenRevocationCache {

    Optional<AccessTokenRevocationCutoff> find(Long memberId);

    // 이미 있으면 더 늦은 기준 시각을 남긴다
    void put(Long memberId, AccessTokenRevocationCutoff cutoff);

    // 앱이 뜰 때 DB 기록으로 통째로 바꾼다
    void replaceAll(Map<Long, AccessTokenRevocationCutoff> cutoffs);

    // 기준 시각이 before 이전인 기록을 지운다. 만료(TTL)를 스스로 처리하는 저장소면 할 일이 없다
    void evictRevokedBefore(Instant before);
}
