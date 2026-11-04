package com.bbcc.kidly.boundedcontext.search.out;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 같은 검색어의 벡터를 잠시 기억해 임베딩 API를 다시 부르지 않는다(#797 리뷰, 호출량 · 비용).
 * 가장 오래 안 쓴 것부터 버리고({@value #MAX_SIZE}개까지), {@link #TTL}이 지나면 다시 만든다.
 * 벡터 하나는 512차원 × 4바이트 ≈ 2KB라 가득 차도 약 2MB다. 서비스 인스턴스마다 따로 둔다.
 * 바꿀 일이 드물어 설정으로 빼지 않는다.
 */
final class KeywordVectorCache {

    static final int MAX_SIZE = 1_000;
    static final Duration TTL = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_SIZE;
        }
    };

    KeywordVectorCache(Clock clock) {
        this.clock = clock;
    }

    synchronized Optional<List<Float>> get(String keyword) {
        Entry entry = entries.get(keyword);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(keyword);
            return Optional.empty();
        }
        return Optional.of(entry.vector());
    }

    synchronized void put(String keyword, List<Float> vector) {
        entries.put(keyword, new Entry(List.copyOf(vector), clock.instant().plus(TTL)));
    }

    private record Entry(List<Float> vector, Instant expiresAt) {
    }
}
