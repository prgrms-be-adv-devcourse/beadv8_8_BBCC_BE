package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * POST /wish/requests(#999). JSON의 문자열 ID("123")도 Long으로 받는다. 칸이 비었거나 겹치면 · 메시지가 길면 400(정책 판단이라 서비스가 본다).
 *
 * @param cartItemIds 조를 내 장바구니 칸
 * @param message 메시지 한 줄(선택)
 */
public record SendWishRequest(
    @NotNull List<Long> cartItemIds,
    String message) {
}
