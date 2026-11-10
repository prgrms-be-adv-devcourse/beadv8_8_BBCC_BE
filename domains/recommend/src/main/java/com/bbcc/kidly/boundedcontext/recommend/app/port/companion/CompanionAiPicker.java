package com.bbcc.kidly.boundedcontext.recommend.app.port.companion;

/**
 * AI가 후보 가운데서 보고 있는 상품과 함께 사면 좋은 상품을 고르는 나가는 연결의 요청서다.
 */
public interface CompanionAiPicker {

    /**
     * AI에게 후보에서 함께 사면 좋은 상품과 그 이유를 고르게 한다. 호출이 실패하면 예외를 던진다.
     * 시간 제한 · 답 재사용 · 하루 호출 상한은 이 요청서를 부르는 쪽이 맡는다.
     */
    CompanionAiAnswer pick(CompanionAiRequest request);
}
