package com.bbcc.kidly.boundedcontext.search.app.port;

import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;

/**
 * 검색이 보내는 도메인 간 이벤트를 내보낸다. 검색은 DB가 없어 아웃박스 없이 바로 보낸다.
 * 보내지 못해도 예외를 던지지 않는다(로그만 남긴다). 검색 응답이 이벤트 전송 때문에 실패하거나 늦어지면 안 된다.
 */
public interface SearchEventSender {

    void send(SearchPerformedEvent event);
}
