package com.bbcc.kidly.boundedcontext.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
import com.bbcc.kidly.boundedcontext.search.app.port.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.search.domain.SearchErrorCode;
import com.bbcc.kidly.boundedcontext.search.in.ProductListController;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 목록 API의 요청 검증과 응답 모양(상품 목록 API와 같음)을 본다. 검색 모듈은 아직 앱에 들어가지 않아,
 * 스프링을 띄우지 않고 컨트롤러와 공통 예외 처리기만으로 확인한다. 기능 테스트는 apps/search-service에서 한다.
 */
@ExtendWith(MockitoExtension.class)
class ProductListControllerTest {

    @Mock
    private ProductListService productListService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductListController(productListService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("목록은 감싸지 않은 페이지 응답이다. 상품 ID는 문자열, 없는 썸네일 · 상호명은 null이다")
    void list() throws Exception {
        given(productListService.list(any())).willReturn(new PageImpl<>(List.of(
            new ProductSearchItem(10L, "여아 원피스", null, null, 15_000L, false, true)), PageRequest.of(0, 20), 21));

        mockMvc.perform(get("/api/v1/products").param("keyword", "원피스"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].productId").value("10"))
            .andExpect(jsonPath("$.content[0].name").value("여아 원피스"))
            .andExpect(jsonPath("$.content[0].thumbnailUrl").isEmpty())
            .andExpect(jsonPath("$.content[0].businessName").isEmpty())
            .andExpect(jsonPath("$.content[0].minPrice").value(15_000))
            .andExpect(jsonPath("$.content[0].soldOut").value(false))
            .andExpect(jsonPath("$.content[0].sizeFree").value(true))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(21))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("없는 정렬 · 형식이 틀린 항목 값 · 범위 밖 페이지 크기는 찾지 않고 400으로 알린다")
    void invalidRequest() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "POPULAR"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/products").param("attributes", "color:3"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
            .andExpect(status().isBadRequest());

        verify(productListService, never()).list(any());
    }

    @Test
    @DisplayName("검색 색인을 쓸 수 없으면 503 ProblemDetail로 알린다")
    void unavailable() throws Exception {
        given(productListService.list(any())).willThrow(new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE));

        mockMvc.perform(get("/api/v1/products"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.title").value(SearchErrorCode.SEARCH_UNAVAILABLE.title()))
            .andExpect(jsonPath("$.instance").value("/api/v1/products"));
    }
}
