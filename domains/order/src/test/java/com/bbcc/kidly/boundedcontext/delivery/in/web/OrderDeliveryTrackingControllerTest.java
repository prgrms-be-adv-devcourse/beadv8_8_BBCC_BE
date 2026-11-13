package com.bbcc.kidly.boundedcontext.delivery.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bbcc.kidly.boundedcontext.delivery.app.DeliveryErrorCode;
import com.bbcc.kidly.boundedcontext.delivery.app.TrackingHistoryService;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.GlobalExceptionHandler;
import com.bbcc.kidly.global.security.LoginMemberArgumentResolver;
import com.bbcc.kidly.global.security.TestMembers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 구매자 택배 진행 이력 API의 409 운송장 오류 응답(#1320). 기능 테스트 설정은 택배 조회가 꺼져 있어(인증값 없음) 이 경로에 닿지 않으므로,
 * 컨트롤러 · 공통 오류 처리기 · 로그인 회원 해석기를 그대로 붙이고 서비스만 대신해 HTTP 응답(상태 · ProblemDetail)을 본다.
 */
class OrderDeliveryTrackingControllerTest {

    private final TrackingHistoryService service = mock(TrackingHistoryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OrderDeliveryTrackingController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new LoginMemberArgumentResolver())
        .build();

    @Test
    @DisplayName("택배사가 운송장 형식 오류로 답하면 409 TRACKING_INVALID_NUMBER(판매자에게 운송장 문의 안내)")
    void invalidTrackingNumberIsConflict() throws Exception {
        when(service.getTracking(1L, 7L, 5L))
            .thenThrow(new BusinessException(DeliveryErrorCode.TRACKING_INVALID_NUMBER));
        HttpHeaders headers = new HttpHeaders();
        TestMembers.buyer(1L).accept(headers);

        mockMvc.perform(get(OrderDeliveryTrackingController.TRACKING_PATH, 7L, 5L).headers(headers))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("운송장 번호를 조회할 수 없습니다"))
            .andExpect(jsonPath("$.detail").value("택배사가 운송장 번호를 확인하지 못했어요. 판매자에게 운송장 번호를 문의해 주세요."));
    }
}
