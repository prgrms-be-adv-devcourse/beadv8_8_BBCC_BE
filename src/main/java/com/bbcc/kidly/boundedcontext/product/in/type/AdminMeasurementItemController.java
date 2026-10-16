package com.bbcc.kidly.boundedcontext.product.in.type;

import com.bbcc.kidly.boundedcontext.product.app.type.MeasurementItemService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemRequest;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.MeasurementItemResult;
import com.bbcc.kidly.boundedcontext.product.in.type.dto.MeasurementItemResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 실측 항목 API. 실측 항목을 만들고 조회한다.
 */
@RestController
@RequestMapping("/api/v1/admin/measurement-items")
@RequiredArgsConstructor
public class AdminMeasurementItemController {

    private final MeasurementItemService measurementItemService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementItemResponse create(
        @Valid @RequestBody MeasurementItemRequest request, HttpServletResponse response) {
        MeasurementItemResult result = measurementItemService.create(request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/measurement-items/" + result.measurementItemId());
        return MeasurementItemResponse.from(result);
    }
}
