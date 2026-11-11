package com.bbcc.kidly.boundedcontext.recommend.domain.size;

public enum Fit {

    ROOMY,
    PROPER,
    TIGHT;

    public Fit tighter() {
        return switch (this) {
            case ROOMY -> PROPER;
            case PROPER, TIGHT -> TIGHT;
        };
    }

    public Fit looser() {
        return switch (this) {
            case TIGHT -> PROPER;
            case PROPER, ROOMY -> ROOMY;
        };
    }

    /**
     * 모델이 준 문자열이 모르는 값일 수 있어, valueOf의 예외 대신 이름이 정확히 같은지 비교하고 없으면 null을 돌려준다.
     */
    static Fit findByName(String name) {
        for (Fit fit : values()) {
            if (fit.name().equals(name)) {
                return fit;
            }
        }
        return null;
    }
}
