package com.bbcc.kidly.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    @DisplayName("업무 예외는 ErrorCode의 상태와 제목으로 ProblemDetail을 만든다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("가격이 바뀌었습니다"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("장바구니 단가와 현재 단가가 다릅니다."))
            .andExpect(jsonPath("$.instance").value("/test/business"));
    }

    @Test
    @DisplayName("업무 예외에 detail을 따로 주면 그 값을 쓴다")
    void businessExceptionWithDetail() throws Exception {
        mockMvc.perform(get("/test/business-detail"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("옵션 1의 단가가 바뀌었습니다."));
    }

    @Test
    @DisplayName("요청 값 검증에 실패하면 400과 필드별 메시지를 detail에 담는다")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("잘못된 요청입니다"))
            .andExpect(jsonPath("$.detail").value("name: 이름을 입력해 주세요."))
            .andExpect(jsonPath("$.instance").value("/test/valid"));
    }

    @Test
    @DisplayName("스프링 MVC 기본 예외도 ProblemDetail 5필드로 응답한다")
    void springMvcException() throws Exception {
        mockMvc.perform(delete("/test/business"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.instance").value("/test/business"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500으로 응답하고 내부 메시지를 노출하지 않는다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("서버 오류가 발생했습니다"))
            .andExpect(jsonPath("$.detail").value("잠시 후 다시 시도해 주세요."))
            .andExpect(jsonPath("$.instance").value("/test/unexpected"));
    }

    private enum TestErrorCode implements ErrorCode {
        PRICE_CHANGED(HttpStatus.CONFLICT, "가격이 바뀌었습니다", "장바구니 단가와 현재 단가가 다릅니다.");

        private final HttpStatus status;
        private final String title;
        private final String detail;

        TestErrorCode(HttpStatus status, String title, String detail) {
            this.status = status;
            this.title = title;
            this.detail = detail;
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String detail() {
            return detail;
        }
    }

    private record TestRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
    }

    @RestController
    private static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(TestErrorCode.PRICE_CHANGED);
        }

        @GetMapping("/test/business-detail")
        void businessDetail() {
            throw new BusinessException(TestErrorCode.PRICE_CHANGED, "옵션 1의 단가가 바뀌었습니다.");
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("DB 비밀번호가 틀렸습니다");
        }
    }
}
