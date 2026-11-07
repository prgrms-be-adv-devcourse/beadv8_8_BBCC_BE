package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.search.out.SearchSynonyms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색 동의어 사전의 형식과 넓히기를 확인한다(#1025).
 */
class SearchSynonymsTest {

    @Test
    @DisplayName("양방향 줄은 어느 말로 찾아도 나머지 말로 넓힌다. 자기 자신은 넣지 않는다")
    void twoWay() {
        SearchSynonyms synonyms = SearchSynonyms.of("바지, 팬츠, 슬랙스");

        assertThat(synonyms.expand("바지")).containsExactly("팬츠", "슬랙스");
        assertThat(synonyms.expand("팬츠")).containsExactly("바지", "슬랙스");
    }

    @Test
    @DisplayName("한 방향 줄은 왼쪽 말로 찾을 때만 넓힌다. 넓힌 말은 여러 단어여도 된다")
    void oneWay() {
        SearchSynonyms synonyms = SearchSynonyms.of("상하복 => 상하 세트", "아우터 => 점퍼, 자켓");

        assertThat(synonyms.expand("상하복")).containsExactly("상하 세트");
        assertThat(synonyms.expand("아우터")).containsExactly("점퍼", "자켓");
        assertThat(synonyms.expand("점퍼")).isEmpty();
    }

    @Test
    @DisplayName("같은 말이 여러 줄에 있으면 넓힌 말을 모두 모은다. 영문은 대소문자를 가리지 않는다")
    void mergeAndIgnoreCase() {
        SearchSynonyms synonyms = SearchSynonyms.of("후드, 후디", "후드 => Hoodie", "# 주석", "");

        assertThat(synonyms.expand("후드")).containsExactly("후디", "Hoodie");
        assertThat(SearchSynonyms.of("Tee, 티").expand("TEE")).containsExactly("티");
    }

    @Test
    @DisplayName("사전에 없는 말은 넓히지 않는다")
    void unknown() {
        assertThat(SearchSynonyms.of("바지, 팬츠").expand("원피스")).isEmpty();
        assertThat(SearchSynonyms.empty().expand("바지")).isEmpty();
    }

    @Test
    @DisplayName("형식이 맞지 않으면 줄 번호와 함께 실패한다")
    void invalid() {
        assertThatThrownBy(() -> SearchSynonyms.of("바지"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("line=1");
        assertThatThrownBy(() -> SearchSynonyms.of("# 주석", "상하 세트, 상하복"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("line=2");
        assertThatThrownBy(() -> SearchSynonyms.of("가 => 나 => 다"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SearchSynonyms.of("바지, , 팬츠"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("서비스가 쓰는 사전 파일을 읽을 수 있다")
    void defaultDictionary() {
        SearchSynonyms synonyms = SearchSynonyms.load("search/synonyms.txt");

        assertThat(synonyms.expand("바지")).contains("팬츠");
        assertThat(synonyms.expand("수영복")).contains("래시가드");
    }
}
