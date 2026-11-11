package com.bbcc.kidly.boundedcontext.recommend.app.companion.dto;

public record CompanionProductResult(
    Long productId,
    String name,
    String thumbnailUrl,
    String businessName,
    Long minPrice,
    String recommendedSize,
    String reason
) {
}
