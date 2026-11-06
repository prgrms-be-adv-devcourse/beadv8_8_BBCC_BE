package com.bbcc.kidly.boundedcontext.order.out.ai;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;

/**
 * 꺼진 진단(#926): 스위치가 꺼졌거나 외부 제공자 키가 없을 때. 부르지 않는다.
 */
public final class DisabledDiagnoser implements StuckOrderDiagnoser {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Diagnosis diagnose(Request request) {
        return Diagnosis.notCalled("DISABLED");
    }
}
