package com.bbcc.kidly.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    @DisplayName("Page를 목록 응답 6필드로 바꾼다")
    void fromPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("결과가 없으면 content는 빈 리스트다")
    void emptyPage() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }
}
