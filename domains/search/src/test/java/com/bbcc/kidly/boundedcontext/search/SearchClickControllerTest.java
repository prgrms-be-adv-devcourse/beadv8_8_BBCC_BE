package com.bbcc.kidly.boundedcontext.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.boundedcontext.search.app.SearchClickService;
import com.bbcc.kidly.boundedcontext.search.app.dto.SearchClickRequest;
import com.bbcc.kidly.boundedcontext.search.in.SearchClickController;
import com.bbcc.kidly.global.error.GlobalExceptionHandler;
import com.bbcc.kidly.global.security.LoginMemberArgumentResolver;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SearchClickControllerTest {

    @Mock
    private SearchClickService searchClickService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchClickController(searchClickService))
            .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("로그인 회원의 클릭은 204로 받고 그 회원으로 기록한다")
    void record() throws Exception {
        click("7", """
            {"keyword": "원피스", "productId": 31, "rank": 3, "sort": "RELEVANCE"}""")
            .andExpect(status().isNoContent());

        var request = ArgumentCaptor.forClass(SearchClickRequest.class);
        verify(searchClickService).record(request.capture(), eq(7L));
        assertThat(request.getValue()).isEqualTo(new SearchClickRequest("원피스", 31L, 3, "RELEVANCE"));
    }

    @Test
    @DisplayName("정렬은 보내지 않아도 된다")
    void withoutSort() throws Exception {
        click("7", """
            {"keyword": "원피스", "productId": 31, "rank": 1}""")
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("회원 헤더가 없거나 잘못되면 본문과 관계없이 401이고 기록하지 않는다")
    void unauthorized() throws Exception {
        click(null, "{}").andExpect(status().isUnauthorized());
        click("abc", """
            {"keyword": "원피스", "productId": 31, "rank": 3}""")
            .andExpect(status().isUnauthorized());

        verify(searchClickService, never()).record(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"keyword\": \"  \", \"productId\": 31, \"rank\": 3}",
        "{\"productId\": 31, \"rank\": 3}",
        "{\"keyword\": \"012345678901234567890123456789012345678901234567890\", \"productId\": 31, \"rank\": 3}",
        "{\"keyword\": \"원피스\", \"productId\": 0, \"rank\": 3}",
        "{\"keyword\": \"원피스\", \"rank\": 3}",
        "{\"keyword\": \"원피스\", \"productId\": 31, \"rank\": 0}",
        "{\"keyword\": \"원피스\", \"productId\": 31, \"rank\": 10001}",
        "{\"keyword\": \"원피스\", \"productId\": 31}",
        "{\"keyword\": \"원피스\", \"productId\": 31, \"rank\": 3, \"sort\": \"POPULAR\"}",
        "{\"keyword\": \"원피스\", \"productId\": \"abc\", \"rank\": 3}"
    })
    @DisplayName("빈 검색어 · 50자 넘는 검색어 · 양수가 아닌 상품 ID · 범위 밖 순위 · 없는 정렬은 400 ProblemDetail이다")
    void invalidRequest(String body) throws Exception {
        click("7", body)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(searchClickService, never()).record(any(), any());
    }

    private ResultActions click(String memberId, String body) throws Exception {
        var request = post("/api/v1/search/clicks").contentType(MediaType.APPLICATION_JSON).content(body);
        if (memberId != null) {
            request.header(MemberHeaders.MEMBER_ID, memberId);
        }
        return mockMvc.perform(request);
    }
}
