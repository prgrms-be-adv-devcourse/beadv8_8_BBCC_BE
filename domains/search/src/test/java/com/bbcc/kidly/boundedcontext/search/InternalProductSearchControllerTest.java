package com.bbcc.kidly.boundedcontext.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.boundedcontext.search.domain.SearchErrorCode;
import com.bbcc.kidly.boundedcontext.search.in.InternalProductSearchController;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.GlobalExceptionHandler;
import com.bbcc.kidly.shared.search.dto.ProductHit;
import com.bbcc.kidly.shared.search.dto.ProductSearchQuery;
import com.bbcc.kidly.shared.search.out.ProductSearcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 서비스 사이 상품 검색 경로의 요청 검증과 응답 모양을 본다. Elasticsearch까지는 apps/search-service의 기능 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class InternalProductSearchControllerTest {

    private static final String PATH = "/internal/products/search";

    @Mock
    private ProductSearcher productSearcher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalProductSearchController(productSearcher))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("조건을 그대로 넘기고 상품 ID · 가격이 숫자인 배열로 답한다. limit을 보내지 않으면 5개다")
    void search() throws Exception {
        given(productSearcher.search(new ProductSearchQuery("원피스", null, 30_000L, 5))).willReturn(List.of(
            new ProductHit(10L, "여아 원피스", null, 25_000L, null, false)));

        mockMvc.perform(get(PATH).param("keyword", "원피스").param("maxPrice", "30000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(10))
            .andExpect(jsonPath("$[0].name").value("여아 원피스"))
            .andExpect(jsonPath("$[0].businessName").isEmpty())
            .andExpect(jsonPath("$[0].price").value(25_000))
            .andExpect(jsonPath("$[0].thumbnailUrl").isEmpty())
            .andExpect(jsonPath("$[0].soldOut").value(false));
        verify(productSearcher).search(new ProductSearchQuery("원피스", null, 30_000L, 5));
    }

    @Test
    @DisplayName("검색어가 없거나 공백 · 50자 초과, 음수 가격, 최저가 > 최고가, limit 범위 밖이면 400이고 검색하지 않는다")
    void rejectsInvalid() throws Exception {
        String[][] invalid = {
            {},
            {"keyword", " "},
            {"keyword", "가".repeat(51)},
            {"keyword", "원피스", "minPrice", "-1"},
            {"keyword", "원피스", "minPrice", "30000", "maxPrice", "10000"},
            {"keyword", "원피스", "limit", "0"},
            {"keyword", "원피스", "limit", "11"},
        };
        for (String[] params : invalid) {
            var request = get(PATH);
            for (int i = 0; i < params.length; i += 2) {
                request.param(params[i], params[i + 1]);
            }
            mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(productSearcher);
    }

    @Test
    @DisplayName("검색 색인을 쓸 수 없으면 503 ProblemDetail이다")
    void unavailable() throws Exception {
        given(productSearcher.search(any())).willThrow(new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE));

        mockMvc.perform(get(PATH).param("keyword", "원피스"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
