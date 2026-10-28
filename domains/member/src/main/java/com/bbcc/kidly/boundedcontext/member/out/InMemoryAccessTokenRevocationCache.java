package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenRevocationCache;
import com.bbcc.kidly.boundedcontext.member.domain.AccessTokenRevocationCutoff;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 무효화 기준 시각을 앱 메모리에 둔다. 앱 인스턴스가 1개라는 전제다.
 * 여러 대가 되면 다른 인스턴스의 무효화를 모르므로 공유 저장소 구현으로 바꾼다.
 */
@Component
public class InMemoryAccessTokenRevocationCache implements AccessTokenRevocationCache {

    private volatile Map<Long, AccessTokenRevocationCutoff> cutoffs = new ConcurrentHashMap<>();

    @Override
    public Optional<AccessTokenRevocationCutoff> find(Long memberId) {
        return Optional.ofNullable(cutoffs.get(memberId));
    }

    @Override
    public void put(Long memberId, AccessTokenRevocationCutoff cutoff) {
        cutoffs.merge(memberId, cutoff, AccessTokenRevocationCutoff::later);
    }

    // 맵을 통째로 바꾸므로 동시에 들어온 put은 유실될 수 있다. 시작할 때만 부른다
    @Override
    public void replaceAll(Map<Long, AccessTokenRevocationCutoff> loaded) {
        cutoffs = new ConcurrentHashMap<>(loaded);
    }

    @Override
    public void evictRevokedBefore(Instant before) {
        cutoffs.values().removeIf(cutoff -> cutoff.revokedAt().isBefore(before));
    }
}
