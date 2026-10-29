package com.bbcc.kidly.boundedcontext.search.out;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 발생 시각을 Elasticsearch 외부 버전(external)으로 넘겨 문서를 저장한다.
 * 외부 버전은 저장된 버전보다 클 때만 저장되고, 같거나 작으면 Elasticsearch가 409로 거절한다.
 * 비교와 저장을 Elasticsearch가 한 번에 하므로 같은 문서의 이벤트가 동시에 와도 늦게 일어난 것이 남는다.
 */
@Slf4j
final class VersionedIndexing {

    private static final int VERSION_CONFLICT = 409;

    private VersionedIndexing() {
    }

    static <T> void indexIfNewer(ElasticsearchClient client, String index, String id, LocalDateTime occurredAt,
        T document) {
        long version = toVersion(occurredAt);
        IndexRequest<T> request = IndexRequest.of(builder -> builder
            .index(index)
            .id(id)
            .version(version)
            .versionType(VersionType.External)
            .document(document));
        try {
            client.index(request);
        } catch (ElasticsearchException e) {
            if (e.status() != VERSION_CONFLICT) {
                throw e;
            }
            log.debug("저장된 문서보다 늦지 않은 이벤트라 덮어쓰지 않습니다. index={}, id={}, version={}", index, id, version);
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch에 문서를 저장하지 못했습니다. index=" + index + ", id=" + id, e);
        }
    }

    // 발생 시각을 마이크로초 단위 수로 바꾼다. 시각끼리 앞뒤만 비교하면 되므로 시간대는 UTC로 고정한다
    static long toVersion(LocalDateTime occurredAt) {
        return occurredAt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L + occurredAt.getNano() / 1_000;
    }
}
