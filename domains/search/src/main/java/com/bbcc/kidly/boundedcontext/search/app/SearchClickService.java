package com.bbcc.kidly.boundedcontext.search.app;

import com.bbcc.kidly.boundedcontext.search.app.dto.SearchClickRequest;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchEventSender;
import com.bbcc.kidly.shared.search.event.SearchResultClickedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그인 회원이 검색 결과에서 상품을 누른 것을 search.result-clicked로 보낸다(#1004). 저장하지 않고 이벤트로만 남긴다.
 * 누른 상품이 색인에 있는지 · 그 순위에 있었는지는 확인하지 않는다. 클릭마다 Elasticsearch를 다시 찾지 않으려는 것이다.
 */
@Service
@RequiredArgsConstructor
public class SearchClickService {

    private final SearchEventSender searchEventSender;

    public void record(SearchClickRequest request, Long memberId) {
        // 검색 이벤트(search.performed)와 같은 검색어로 묶이도록 같은 규칙(앞뒤 공백 빼기)으로 담는다
        searchEventSender.send(new SearchResultClickedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new SearchResultClickedEvent.Data(memberId, request.keyword().strip(), request.productId(),
                request.rank(), request.sortOrDefault(), request.childId())));
    }
}
