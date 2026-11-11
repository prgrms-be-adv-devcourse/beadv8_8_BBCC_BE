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
}
