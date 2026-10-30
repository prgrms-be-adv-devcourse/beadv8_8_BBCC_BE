package com.bbcc.kidly.boundedcontext.order.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /admin/orders/dead-letters/{deadLetterId}/close(#601). 처리하지 않고 닫는 이유를 남기므로 메모가 필요하다.
 *
 * @param memo 확인한 내용 · 닫는 이유(500자까지)
 */
public record CloseDeadLetterRequest(
    @NotBlank @Size(max = 500) String memo) {
}
