package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;

/**
 * 외부 호출 없이 정해진 메모를 돌려주는 진단(#926, provider fake). 로컬 · 테스트에서 흐름을 확인할 때 쓴다.
 * 응답은 실제 어댑터와 같은 해석기({@link AiDiagnosisPrompt})를 거친다.
 */
public final class FakeDiagnoser implements StuckOrderDiagnoser {

    static final String PROVIDER = "fake";

    private final AiDiagnosisPrompt prompt;
    private final String model;

    public FakeDiagnoser(AiDiagnosisPrompt prompt, String model) {
        this.prompt = prompt;
        this.model = model;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Diagnosis diagnose(Request request) {
        // 요청 글을 실제로 만들어 본다(만들기 실패도 실제와 같이 드러나게)
        prompt.messages(request);
        String content = """
            {"summary":"가짜 진단: %s 상태로 멈췄습니다","recommendedAction":"관리자 목록에서 상태를 확인하세요","confidence":"LOW"}"""
            .formatted(request.stuckKind());
        return prompt.parse(content, PROVIDER, model);
    }
}
