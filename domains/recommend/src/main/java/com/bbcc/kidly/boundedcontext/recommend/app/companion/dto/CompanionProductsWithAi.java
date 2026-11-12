package com.bbcc.kidly.boundedcontext.recommend.app.companion.dto;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 바로 쓸 수 있는 규칙 결과와, AI 답이 오면 끝나는 AI 결과를 함께 담는다.
 * AI 결과는 AI 답을 얻지 못하면 빈 값으로 끝난다. 저장한 AI 답이 있으면 이미 끝나 있다.
 */
public record CompanionProductsWithAi(
    CompanionProductsResult rule,
    CompletableFuture<Optional<CompanionProductsResult>> ai
) {
}
