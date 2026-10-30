package com.bbcc.kidly.boundedcontext.search.app.port;

/**
 * 검색 색인을 쓸 수 없다. Elasticsearch에 연결하지 못했거나 오류로 답한 경우다(색인 없음 · 과부하 등).
 * 다시 해 보면 될 수 있는 장애라 목록 API는 503으로 알린다.
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
