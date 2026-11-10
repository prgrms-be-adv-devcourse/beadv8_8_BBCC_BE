package com.bbcc.kidly.boundedcontext.recommend.app.port.companion;

import java.util.List;

/**
 * AI에 보내는 상품 하나다. 브랜드는 없으면 null이다.
 */
public record CompanionAiProduct(Long productId, String name, List<String> categoryPathNames, String brand) {
}
