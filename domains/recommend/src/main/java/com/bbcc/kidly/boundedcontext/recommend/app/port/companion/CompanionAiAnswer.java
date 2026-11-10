package com.bbcc.kidly.boundedcontext.recommend.app.port.companion;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionAiPick;
import java.util.List;

/**
 * AI의 답을 그대로 담는다. 칸 이름이 AI 답과 저장한 답의 JSON 칸 이름이라서, 저장했다가 다시 읽을 수 있다.
 * 후보에 없는 상품이나 중복을 거르는 일은 이 객체가 아니라 함께 사면 좋은 상품을 고르는 domain이 한다.
 */
public record CompanionAiAnswer(List<CompanionAiPick> picks) {
}
