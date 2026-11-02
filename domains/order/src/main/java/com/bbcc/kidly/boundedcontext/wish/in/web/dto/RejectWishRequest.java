package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /wish-requests/{requestId}/rejection(#701).
 *
 * @param reason 거절 사유(TOO_EXPENSIVE · ALREADY_HAVE · NEXT_TIME · OTHER). 목록에 없으면 400
 */
public record RejectWishRequest(@NotNull String reason) {
}
