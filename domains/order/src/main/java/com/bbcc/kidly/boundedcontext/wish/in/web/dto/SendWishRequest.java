package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * POST /wish-requests(#701). JSON의 문자열 ID("123")도 Long으로 받는다. 줄이 비었거나 겹치면 · 메시지가 길면 400(정책 판단이라 서비스가 본다).
 *
 * @param childId 조르는 자녀(프로필 ID)
 * @param wishItemIds 조를 공용 장바구니 줄
 * @param message 메시지 한 줄(선택)
 */
public record SendWishRequest(
    @NotNull Long childId,
    @NotNull List<Long> wishItemIds,
    String message) {
}
