package com.bbcc.kidly.boundedcontext.recommend.app.port.companion;

import java.util.List;

/**
 * AI에게 보내는 요청이다. 보고 있는 상품과, 그 가운데서 고를 후보 상품들을 담는다.
 */
public record CompanionAiRequest(CompanionAiProduct viewed, List<CompanionAiProduct> candidates) {
}
