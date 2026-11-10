package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.search.app.SearchClickService;
import com.bbcc.kidly.boundedcontext.search.app.dto.SearchClickRequest;
import com.bbcc.kidly.boundedcontext.search.app.port.SearchEventSender;
import com.bbcc.kidly.shared.event.DomainEvent;
import com.bbcc.kidly.shared.search.event.SearchResultClickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchClickServiceTest {

    @Mock
    private SearchEventSender searchEventSender;

    @InjectMocks
    private SearchClickService searchClickService;

    @Test
    @DisplayName("클릭은 회원 키의 search.result-clicked로 보내고, 검색어는 앞뒤 공백을 뺀다")
    void sendsClick() {
        searchClickService.record(new SearchClickRequest("  원피스 여아용 ", 31L, 3, "PRICE_ASC", null), 7L);

        SearchResultClickedEvent event = sentEvent();
        assertThat(event.eventType()).isEqualTo("search.result-clicked");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("7");
        assertThat(event.data()).isEqualTo(
            new SearchResultClickedEvent.Data(7L, "원피스 여아용", 31L, 3, "PRICE_ASC"));
    }

    @Test
    @DisplayName("자녀를 골랐으면 클릭 이벤트에 그 자녀 ID를 담고, 고르지 않았으면 null이다(#1089)")
    void childId() {
        searchClickService.record(new SearchClickRequest("원피스", 31L, 1, null, 11L), 7L);
        searchClickService.record(new SearchClickRequest("원피스", 31L, 1, null, null), 7L);

        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(searchEventSender, times(2)).send(event.capture());
        assertThat(((SearchResultClickedEvent) event.getAllValues().get(0)).data().childId()).isEqualTo(11L);
        assertThat(((SearchResultClickedEvent) event.getAllValues().get(1)).data().childId()).isNull();
    }

    @Test
    @DisplayName("정렬을 보내지 않으면 상품 목록 API의 기본 정렬(NEWEST)로 담는다")
    void defaultSort() {
        searchClickService.record(new SearchClickRequest("원피스", 31L, 1, null, null), 7L);

        assertThat(sentEvent().data().sort()).isEqualTo("NEWEST");
    }

    private SearchResultClickedEvent sentEvent() {
        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(searchEventSender).send(event.capture());
        assertThat(event.getValue()).isInstanceOf(SearchResultClickedEvent.class);
        return (SearchResultClickedEvent) event.getValue();
    }
}
